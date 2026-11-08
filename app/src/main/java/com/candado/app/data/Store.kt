package com.candado.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray

/** Almacenamiento local de toda la configuración. Nada sale del teléfono. */
object Store {
    lateinit var prefs: SharedPreferences
        private set

    /** Se incrementa en cada cambio para que la pantalla se actualice. */
    val rev = mutableIntStateOf(0)

    @Volatile private var lockCache: List<Lock>? = null

    val DEFAULT_KEYWORDS = setOf(
        "porno", "pornografia", "porn", "xxx", "xvideos", "xnxx", "pornhub", "onlyfans",
        "hentai", "nsfw", "desnuda", "desnudas", "nudes", "sexo explicito", "camgirl", "webcam sexy"
    )

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("candado", Context.MODE_PRIVATE)
        if (!prefs.contains("keywords")) prefs.edit().putStringSet("keywords", DEFAULT_KEYWORDS).apply()
    }

    private fun bump() { rev.intValue = rev.intValue + 1 }

    private fun set(key: String) = prefs.getStringSet(key, emptySet())!!.toSet()
    private fun putSet(key: String, v: Set<String>) { prefs.edit().putStringSet(key, HashSet(v)).apply(); bump() }
    private fun putBool(key: String, v: Boolean) { prefs.edit().putBoolean(key, v).apply(); bump() }

    var blockedApps: Set<String>
        get() = set("apps")
        set(v) = putSet("apps", v)

    var keywords: Set<String>
        get() = set("keywords")
        set(v) = putSet("keywords", v)

    var blockedDomains: Set<String>
        get() = set("domains")
        set(v) = putSet("domains", v)

    /** Apps de trabajo que NO pasan por el filtro (ej. punto de venta). */
    var vpnExcluded: Set<String>
        get() = set("vpn_excluded")
        set(v) = putSet("vpn_excluded", v)

    var filterEnabled: Boolean
        get() = prefs.getBoolean("filter", false)
        set(v) = putBool("filter", v)

    var safeSearch: Boolean
        get() = prefs.getBoolean("safesearch", true)
        set(v) = putBool("safesearch", v)

    var blockDoh: Boolean
        get() = prefs.getBoolean("blockdoh", true)
        set(v) = putBool("blockdoh", v)

    var keywordsEnabled: Boolean
        get() = prefs.getBoolean("kw", false)
        set(v) = putBool("kw", v)

    var upstream: Upstream
        get() = Upstream.entries.firstOrNull { it.name == prefs.getString("upstream", null) } ?: Upstream.CLOUDFLARE_FAMILIA
        set(v) { prefs.edit().putString("upstream", v.name).apply(); bump() }

    /** ID de configuración de NextDNS (6 caracteres, ej. abc123). */
    var nextDnsId: String
        get() = prefs.getString("nextdns", "") ?: ""
        set(v) { prefs.edit().putString("nextdns", v).apply(); bump() }

    /** Acepta "abc123", "abc123.dns.nextdns.io" o "https://dns.nextdns.io/abc123". */
    fun parseNextDnsId(input: String): String? =
        Regex("(?i)(?:^|[./])([a-f0-9]{6})(?:\\.dns\\.nextdns\\.io|$|[/?])").find(input.trim())?.groupValues?.get(1)?.lowercase()

    fun locks(): List<Lock> {
        lockCache?.let { return it }
        val raw = prefs.getString("locks", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val list = (0 until arr.length()).mapNotNull {
            try { Lock.fromJson(arr.getJSONObject(it)) } catch (e: Exception) { null }
        }
        lockCache = list
        return list
    }

    @Synchronized
    fun saveLocks(list: List<Lock>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("locks", arr.toString()).commit()
        lockCache = list
        bump()
    }

    fun lock(id: String): Lock? = locks().firstOrNull { it.id == id }

    @Synchronized
    fun updateLock(id: String, f: (Lock) -> Lock) {
        saveLocks(locks().map { if (it.id == id) f(it) else it })
    }

    @Synchronized
    fun addLock(l: Lock) = saveLocks(locks() + l)

    @Synchronized
    fun removeLock(id: String) = saveLocks(locks().filterNot { it.id == id })
}
