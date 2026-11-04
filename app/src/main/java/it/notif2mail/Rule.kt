package it.notif2mail

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/**
 * Una regola dice: per questa app (o per tutte) inoltra le notifiche
 * che passano i filtri, nei giorni/orari indicati.
 */
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val pkg: String = ALL,
    val appLabel: String = "Tutte le app",
    val include: String = "",
    val exclude: String = "",
    /** Destinatari solo per questa regola; vuoto = quelli del canale mail */
    val recipients: String = "",
    /** Bit 0 = lunedì ... bit 6 = domenica */
    val days: Int = 127,
    val useWindow: Boolean = false,
    val startMin: Int = 8 * 60,
    val endMin: Int = 20 * 60,
    /** 0 = una mail per notifica; N = un riepilogo ogni N minuti */
    val digestMinutes: Int = 0,
    val enabled: Boolean = true
) {

    fun matchesText(haystackLower: String): Boolean {
        val inc = words(include)
        val exc = words(exclude)
        if (inc.isNotEmpty() && inc.none { haystackLower.contains(it) }) return false
        if (exc.any { haystackLower.contains(it) }) return false
        return true
    }

    fun activeAt(cal: Calendar): Boolean {
        val bit = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // lunedì = 0
        if (days and (1 shl bit) == 0) return false
        if (!useWindow) return true
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (startMin <= endMin) now in startMin..endMin else (now >= startMin || now <= endMin)
    }

    fun summary(): String {
        val parts = mutableListOf<String>()
        if (include.isNotBlank()) parts += "contiene: ${include.trim()}"
        if (exclude.isNotBlank()) parts += "esclude: ${exclude.trim()}"
        if (days != 127) {
            parts += DAY_NAMES.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(" ")
        }
        if (useWindow) parts += "${fmtTime(startMin)}–${fmtTime(endMin)}"
        if (digestMinutes > 0) parts += "riepilogo ogni $digestMinutes min"
        if (recipients.isNotBlank()) parts += "→ ${recipients.trim()}"
        return if (parts.isEmpty()) "Tutte le notifiche" else parts.joinToString(" · ")
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("pkg", pkg)
        .put("appLabel", appLabel)
        .put("include", include)
        .put("exclude", exclude)
        .put("recipients", recipients)
        .put("days", days)
        .put("useWindow", useWindow)
        .put("startMin", startMin)
        .put("endMin", endMin)
        .put("digestMinutes", digestMinutes)
        .put("enabled", enabled)

    companion object {
        const val ALL = "*"
        val DAY_NAMES = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

        fun words(s: String): List<String> =
            s.split(',', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        fun fmtTime(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

        fun fromJson(o: JSONObject) = Rule(
            id = o.optString("id", UUID.randomUUID().toString()),
            pkg = o.optString("pkg", ALL),
            appLabel = o.optString("appLabel", "Tutte le app"),
            include = o.optString("include", ""),
            exclude = o.optString("exclude", ""),
            recipients = o.optString("recipients", ""),
            days = o.optInt("days", 127),
            useWindow = o.optBoolean("useWindow", false),
            startMin = o.optInt("startMin", 8 * 60),
            endMin = o.optInt("endMin", 20 * 60),
            digestMinutes = o.optInt("digestMinutes", 0),
            enabled = o.optBoolean("enabled", true)
        )

        fun listToJson(list: List<Rule>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(s: String): List<Rule> = try {
            val a = JSONArray(s)
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

object RuleMatcher {
    /**
     * Prima regola attiva che accetta la notifica. Le regole di una app
     * specifica vengono provate prima di quelle "Tutte le app".
     */
    fun firstMatch(rules: List<Rule>, pkg: String, title: String, text: String, now: Calendar): Rule? {
        val haystack = "$title $text".lowercase()
        return rules
            .filter { it.enabled }
            .sortedBy { if (it.pkg == Rule.ALL) 1 else 0 }
            .firstOrNull { (it.pkg == Rule.ALL || it.pkg == pkg) && it.matchesText(haystack) && it.activeAt(now) }
    }
}
