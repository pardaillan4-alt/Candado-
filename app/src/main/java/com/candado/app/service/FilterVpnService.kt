package com.candado.app.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.candado.app.core.Guard
import com.candado.app.data.Store
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Filtro de contenido para todo el teléfono, sin servidores propios:
 * una VPN local que solo recibe las consultas DNS. Bloquea lo de la lista y manda
 * el resto a un DNS familiar (Cloudflare Familia / CleanBrowsing / OpenDNS).
 * El tráfico normal NO pasa por la VPN, así que no gasta batería ni hace lento el internet.
 */
class FilterVpnService : VpnService() {

    companion object {
        @Volatile var running = false
            private set

        private const val VADDR = "10.111.222.1"
        private const val VDNS = "10.111.222.2"

        /** DNS públicos que algunas apps usan "a fuerza": también se capturan y filtran. */
        private val HIJACK = listOf(
            "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112",
            "208.67.222.222", "208.67.220.220", "94.140.14.14", "94.140.15.15", "76.76.2.0", "76.76.10.0"
        )

        fun start(ctx: Context) {
            if (VpnService.prepare(ctx) == null) {
                try { ctx.startService(Intent(ctx, FilterVpnService::class.java)) } catch (_: Exception) {}
            }
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, FilterVpnService::class.java).setAction("STOP"))
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var pool: ExecutorService? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        if (intent?.action == "STOP") {
            if (!Guard.filterActive()) { shutdown(); stopSelf() }
            return START_NOT_STICKY
        }
        if (!running) establish()
        return START_STICKY
    }

    private fun establish() {
        try {
            val b = Builder()
                .setSession("Candado – filtro")
                .addAddress(VADDR, 32)
                .addDnsServer(VDNS)
                .addRoute(VDNS, 32)
            HIJACK.forEach { b.addRoute(it, 32) }
            try { b.addDisallowedApplication(packageName) } catch (_: Exception) {}
            if (Build.VERSION.SDK_INT >= 29) { b.setBlocking(true); b.setMetered(false) }
            val fd = b.establish() ?: return
            tun = fd
            running = true
            pool = Executors.newFixedThreadPool(6)
            reader = Thread({ loop(fd) }, "candado-dns").also { it.start() }
        } catch (e: Exception) {
            running = false
        }
    }

    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        while (running) {
            val n = try { input.read(buf) } catch (e: Exception) { if (running) -2 else break }
            if (n <= 0) { try { Thread.sleep(15) } catch (_: InterruptedException) { break }; continue }
            val pkt = buf.copyOf(n)
            if (!isIpv4UdpDns(pkt)) continue // todo lo demás hacia los DNS capturados (DoT/DoH) se descarta
            pool?.execute {
                val resp = handle(pkt) ?: return@execute
                try { synchronized(output) { output.write(resp) } } catch (_: Exception) {}
            }
        }
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun isIpv4UdpDns(p: ByteArray): Boolean {
        if (p.size < 28) return false
        if ((p[0].toInt() ushr 4) != 4) return false
        if ((p[9].toInt() and 0xFF) != 17) return false
        val ihl = (p[0].toInt() and 0x0F) * 4
        return p.size >= ihl + 8 && u16(p, ihl + 2) == 53
    }

    private fun handle(pkt: ByteArray): ByteArray? {
        val ihl = (pkt[0].toInt() and 0x0F) * 4
        val udpLen = u16(pkt, ihl + 4)
        val end = minOf(pkt.size, ihl + udpLen)
        if (end <= ihl + 8 + 12) return null
        val query = pkt.copyOfRange(ihl + 8, end)

        val q = Dns.parseQuestion(query) ?: return null
        val answer: ByteArray? = when (val v = DnsFilter.verdict(q.name)) {
            is DnsFilter.Verdict.Block -> Dns.blocked(query, q)
            is DnsFilter.Verdict.Rewrite -> Dns.rewrite(query, q, v.ipv4)
            is DnsFilter.Verdict.Forward -> forward(query)
        }
        return answer?.let { buildIpUdp(pkt, ihl, it) }
    }

    private fun forward(query: ByteArray): ByteArray? {
        for (ip in Store.upstream.ips) {
            try {
                DatagramSocket().use { s ->
                    protect(s)
                    s.soTimeout = 4000
                    s.send(DatagramPacket(query, query.size, InetAddress.getByName(ip), 53))
                    val rb = ByteArray(4096)
                    val rp = DatagramPacket(rb, rb.size)
                    s.receive(rp)
                    return rb.copyOf(rp.length)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /** Arma el paquete IPv4+UDP de respuesta (origen y destino invertidos). */
    private fun buildIpUdp(req: ByteArray, ihl: Int, dns: ByteArray): ByteArray {
        val total = 20 + 8 + dns.size
        val r = ByteArray(total)
        r[0] = 0x45; r[1] = 0
        r[2] = (total ushr 8).toByte(); r[3] = total.toByte()
        r[6] = 0x40; r[8] = 64; r[9] = 17
        System.arraycopy(req, 16, r, 12, 4) // origen = destino original
        System.arraycopy(req, 12, r, 16, 4) // destino = origen original
        var sum = 0
        for (i in 0 until 20 step 2) sum += u16(r, i)
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        val cs = sum.inv() and 0xFFFF
        r[10] = (cs ushr 8).toByte(); r[11] = cs.toByte()
        // UDP
        r[20] = 0; r[21] = 53
        r[22] = req[ihl]; r[23] = req[ihl + 1]
        val ul = 8 + dns.size
        r[24] = (ul ushr 8).toByte(); r[25] = ul.toByte()
        r[26] = 0; r[27] = 0 // checksum opcional en IPv4
        System.arraycopy(dns, 0, r, 28, dns.size)
        return r
    }

    private fun shutdown() {
        running = false
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        reader?.interrupt()
        pool?.shutdownNow()
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}

/** Utilidades mínimas de DNS. */
object Dns {
    class Question(val name: String, val type: Int, val end: Int)

    fun parseQuestion(q: ByteArray): Question? {
        if (q.size < 17) return null
        val qd = ((q[4].toInt() and 0xFF) shl 8) or (q[5].toInt() and 0xFF)
        if (qd < 1) return null
        var i = 12
        val sb = StringBuilder()
        while (i < q.size) {
            val len = q[i].toInt() and 0xFF
            if (len == 0) { i++; break }
            if (len and 0xC0 != 0) return null
            if (i + 1 + len > q.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(q, i + 1, len, Charsets.US_ASCII))
            i += 1 + len
        }
        if (i + 4 > q.size) return null
        val type = ((q[i].toInt() and 0xFF) shl 8) or (q[i + 1].toInt() and 0xFF)
        return Question(sb.toString(), type, i + 4)
    }

    private fun header(q: ByteArray, rcode: Int, an: Int): ByteArray {
        val h = ByteArray(12)
        h[0] = q[0]; h[1] = q[1]
        h[2] = (0x80 or (q[2].toInt() and 0x01)).toByte() // respuesta + RD copiado
        h[3] = (0x80 or rcode).toByte()                     // RA + código
        h[4] = 0; h[5] = 1
        h[6] = 0; h[7] = an.toByte()
        return h
    }

    /** Dominio bloqueado: responde 0.0.0.0 para A y vacío para lo demás. */
    fun blocked(q: ByteArray, qq: Question): ByteArray =
        if (qq.type == 1) rewrite(q, qq, byteArrayOf(0, 0, 0, 0)) else header(q, 3, 0) + q.copyOfRange(12, qq.end)

    fun rewrite(q: ByteArray, qq: Question, ip: ByteArray): ByteArray {
        val question = q.copyOfRange(12, qq.end)
        if (qq.type != 1) return header(q, 0, 0) + question // AAAA etc.: sin respuesta, se usa IPv4
        val ans = byteArrayOf(
            0xC0.toByte(), 0x0C, 0, 1, 0, 1, 0, 0, 0x01, 0x2C, 0, 4
        ) + ip
        return header(q, 0, 1) + question + ans
    }
}
