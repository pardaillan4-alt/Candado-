package com.candado.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Qué protege un candado. */
enum class Target(val titulo: String, val desc: String) {
    APPS("Apps bloqueadas", "No se pueden abrir las apps de tu lista ni quitarlas de la lista."),
    FILTRO("Filtro de internet", "Bloquea contenido para adultos en todo el celular (todas las apps y navegadores). No se puede apagar."),
    DNS_VPN("DNS y VPN", "Cierra las pantallas de DNS privado, VPN y solicitudes de otras VPN para que no se pueda evadir el filtro."),
    AJUSTES("Ajustes críticos", "Impide desactivar o desinstalar Candado, cambiar fecha/hora, opciones de desarrollador y restablecer de fábrica."),
    PALABRAS("Palabras prohibidas", "Cierra cualquier pantalla (navegador, redes, apps) que muestre una palabra de tu lista.")
}

/** Cómo se abre un candado. */
enum class Method(val titulo: String, val desc: String) {
    PIN("NIP", "Número de 4 a 12 dígitos."),
    PASSWORD("Contraseña", "Texto de 6 caracteres o más."),
    QR("Código QR", "Se genera un QR único. Imprímelo o dáselo a alguien de confianza; sin él no se abre."),
    BIOMETRIC("Biométrico", "Huella o rostro; tú eliges qué tipo de biométrico se acepta."),
    RANDOM_TEXT("Texto aleatorio", "Hay que copiar a mano un texto largo al azar (no se puede pegar). Frena los impulsos."),
    COOLDOWN("Espera de enfriamiento", "Pides abrirlo y debes esperar las horas que elijas antes de que se abra.")
}

enum class BioKind(val titulo: String, val desc: String) {
    HUELLAS_ACTUALES(
        "Solo los biométricos registrados HOY",
        "Ligado a las huellas/rostro que el teléfono tiene ahora. Si alguien registra una huella nueva, este candado deja de aceptar biométricos (agrega otro método de respaldo)."
    ),
    FUERTE("Biométrico fuerte (huella)", "Huella o rostro de seguridad alta (clase 3)."),
    CUALQUIERA("Cualquier biométrico (huella o rostro)", "Acepta también el desbloqueo facial común (clase 2).")
}

data class Lock(
    val id: String,
    val nombre: String,
    val targets: Set<Target>,
    val methods: Set<Method>,
    val requireAll: Boolean,
    val createdAt: Long,
    /** 0 = no es estricto. Si es mayor que "ahora", nada lo abre. */
    val strictUntil: Long,
    val passMinutes: Int,
    val salt: String,
    val pinHash: String? = null,
    val pwdHash: String? = null,
    val qrHash: String? = null,
    val bioKind: BioKind? = null,
    val bioAlias: String? = null,
    val randomLen: Int = 50,
    val cooldownHours: Int = 24,
    val cooldownRequestedAt: Long = 0,
    /** Hasta cuándo está abierto (pase temporal) después de desbloquearlo. */
    val passUntil: Long = 0,
    val fails: Int = 0,
    val lastFailAt: Long = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("nombre", nombre)
        put("targets", JSONArray(targets.map { it.name }))
        put("methods", JSONArray(methods.map { it.name }))
        put("requireAll", requireAll); put("createdAt", createdAt)
        put("strictUntil", strictUntil); put("passMinutes", passMinutes)
        put("salt", salt)
        pinHash?.let { put("pinHash", it) }
        pwdHash?.let { put("pwdHash", it) }
        qrHash?.let { put("qrHash", it) }
        bioKind?.let { put("bioKind", it.name) }
        bioAlias?.let { put("bioAlias", it) }
        put("randomLen", randomLen); put("cooldownHours", cooldownHours)
        put("cooldownRequestedAt", cooldownRequestedAt); put("passUntil", passUntil)
        put("fails", fails); put("lastFailAt", lastFailAt)
    }

    companion object {
        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

        fun fromJson(o: JSONObject) = Lock(
            id = o.getString("id"),
            nombre = o.optString("nombre", "Candado"),
            targets = o.getJSONArray("targets").strings().mapNotNull { n -> Target.entries.firstOrNull { it.name == n } }.toSet(),
            methods = o.getJSONArray("methods").strings().mapNotNull { n -> Method.entries.firstOrNull { it.name == n } }.toSet(),
            requireAll = o.optBoolean("requireAll", false),
            createdAt = o.optLong("createdAt"),
            strictUntil = o.optLong("strictUntil"),
            passMinutes = o.optInt("passMinutes", 15),
            salt = o.getString("salt"),
            pinHash = o.optString("pinHash").ifEmpty { null },
            pwdHash = o.optString("pwdHash").ifEmpty { null },
            qrHash = o.optString("qrHash").ifEmpty { null },
            bioKind = o.optString("bioKind").let { n -> BioKind.entries.firstOrNull { it.name == n } },
            bioAlias = o.optString("bioAlias").ifEmpty { null },
            randomLen = o.optInt("randomLen", 50),
            cooldownHours = o.optInt("cooldownHours", 24),
            cooldownRequestedAt = o.optLong("cooldownRequestedAt"),
            passUntil = o.optLong("passUntil"),
            fails = o.optInt("fails"),
            lastFailAt = o.optLong("lastFailAt")
        )
    }
}

enum class Upstream(val titulo: String, val ips: List<String>) {
    CLOUDFLARE_FAMILIA("Cloudflare Familia (adultos + malware)", listOf("1.1.1.3", "1.0.0.3")),
    CLEANBROWSING("CleanBrowsing Familia (más estricto, incluye proxies/VPN)", listOf("185.228.168.168", "185.228.169.168")),
    OPENDNS_FAMILY("OpenDNS FamilyShield", listOf("208.67.222.123", "208.67.220.123"))
}
