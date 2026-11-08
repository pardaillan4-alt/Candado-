package com.candado.app.service

import com.candado.app.data.Store

/** Decide qué hacer con cada nombre de dominio. */
object DnsFilter {
    sealed class Verdict {
        object Block : Verdict()
        object Forward : Verdict()
        /** Responder con esta IPv4 (búsqueda segura forzada). */
        class Rewrite(val ipv4: ByteArray) : Verdict()
    }

    private val GOOGLE_SAFE = byteArrayOf(216.toByte(), 239.toByte(), 38, 120)  // forcesafesearch.google.com
    private val YOUTUBE_STRICT = byteArrayOf(216.toByte(), 239.toByte(), 38, 120) // restrict.youtube.com
    private val BING_STRICT = byteArrayOf(204.toByte(), 79, 197.toByte(), 220.toByte()) // strict.bing.com

    private val ADULT = listOf(
        "porn", "xvideos", "xhamster", "xnxx", "redtube", "youporn", "onlyfans", "chaturbate", "hentai",
        "brazzers", "spankbang", "stripchat", "camsoda", "livejasmin", "bongacams", "eporner", "tnaflix",
        "motherless", "rule34", "nhentai", "fansly", "xxx", "erome", "sexcam", "porno", "hclips", "txxx",
        "beeg.com", "tube8", "sunporno", "fapello", "manyvids", "cam4", "myfreecams", "imagefap"
    )

    /** Servidores de DNS seguro (DoH/DoT) que permitirían saltarse el filtro desde un navegador. */
    private val DOH = listOf(
        "dns.google", "dns.google.com", "cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com",
        "mozilla.cloudflare-dns.com", "chrome.cloudflare-dns.com", "dns.quad9.net", "doh.opendns.com",
        "dns.nextdns.io", "doh.cleanbrowsing.org", "dns.adguard.com", "dns.adguard-dns.com", "doh.mullvad.net",
        "dns.controld.com", "use-application-dns.net", "doh.dns.sb", "dns.alidns.com", "doh.pub"
    )

    private val YT = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtubei.googleapis.com",
        "youtube.googleapis.com", "www.youtube-nocookie.com"
    )

    private fun matchesDomain(name: String, d: String) = name == d || name.endsWith(".$d")

    private fun isGoogleSearch(name: String): Boolean {
        val n = name.removePrefix("www.")
        if (!n.startsWith("google.")) return false
        val tld = n.removePrefix("google.")
        return tld.isNotEmpty() && tld.length <= 6 && tld.all { it.isLetter() || it == '.' }
    }

    fun verdict(rawName: String): Verdict {
        val name = rawName.lowercase().trimEnd('.')
        if (name.isEmpty()) return Verdict.Forward

        if (Store.workMode) {
            if (Store.workAllowed.none { matchesDomain(name, it.lowercase()) }) return Verdict.Block
            if (name.startsWith("youtube") || name.contains(".youtube")) return Verdict.Block
        }

        if (Store.blockedDomains.any { matchesDomain(name, it.lowercase()) }) return Verdict.Block
        if (ADULT.any { name.contains(it) }) return Verdict.Block
        // Con NextDNS y candado en el filtro, no se puede entrar al panel para aflojarlo.
        if (Store.upstream == com.candado.app.data.Upstream.NEXTDNS && com.candado.app.core.Guard.isProtected(com.candado.app.data.Target.FILTRO) &&
            (matchesDomain(name, "my.nextdns.io") || matchesDomain(name, "api.nextdns.io") || name == "nextdns.io" || name == "www.nextdns.io")) return Verdict.Block
        if (Store.blockDoh && DOH.any { matchesDomain(name, it) } && name != "family.cloudflare-dns.com") return Verdict.Block

        if (Store.safeSearch) {
            if (isGoogleSearch(name)) return Verdict.Rewrite(GOOGLE_SAFE)
            if (name in YT) return Verdict.Rewrite(YOUTUBE_STRICT)
            if (name == "bing.com" || name == "www.bing.com") return Verdict.Rewrite(BING_STRICT)
            if (name == "duckduckgo.com" || name == "www.duckduckgo.com" || name == "start.duckduckgo.com") return Verdict.Block
        }
        return Verdict.Forward
    }
}
