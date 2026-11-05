package it.notif2mail

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Tutte le impostazioni dell'app, salvate cifrate (password e token inclusi).
 */
class Config(context: Context) {

    private val appCtx = context.applicationContext

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(appCtx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appCtx,
            "notif2mail_cfg",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    init {
        migrateIfNeeded()
    }

    private fun str(key: String, def: String) = prefs.getString(key, def) ?: def
    private fun putStr(key: String, v: String) = prefs.edit().putString(key, v).apply()
    private fun bool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun putBool(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    var enabled: Boolean
        get() = bool("enabled", false)
        set(v) = putBool("enabled", v)

    // --- Canale mail (SMTP) ---
    var mailEnabled: Boolean
        get() = bool("mailEnabled", true)
        set(v) = putBool("mailEnabled", v)

    var host: String
        get() = str("host", "smtp.gmail.com")
        set(v) = putStr("host", v.trim())

    var port: Int
        get() = prefs.getInt("port", 465)
        set(v) = prefs.edit().putInt("port", v).apply()

    /** "SSL", "STARTTLS" oppure "NONE" */
    var security: String
        get() = str("security", "SSL")
        set(v) = putStr("security", v)

    var user: String
        get() = str("user", "")
        set(v) = putStr("user", v.trim())

    var password: String
        get() = str("password", "")
        set(v) = putStr("password", v)

    var from: String
        get() = str("from", "")
        set(v) = putStr("from", v.trim())

    /** Uno o più destinatari separati da virgola */
    var to: String
        get() = str("to", "")
        set(v) = putStr("to", v.trim())

    // --- Telegram ---
    var tgEnabled: Boolean
        get() = bool("tgEnabled", false)
        set(v) = putBool("tgEnabled", v)

    var tgToken: String
        get() = str("tgToken", "")
        set(v) = putStr("tgToken", v.trim())

    var tgChatId: String
        get() = str("tgChatId", "")
        set(v) = putStr("tgChatId", v.trim())

    // --- ntfy ---
    var ntfyEnabled: Boolean
        get() = bool("ntfyEnabled", false)
        set(v) = putBool("ntfyEnabled", v)

    var ntfyServer: String
        get() = str("ntfyServer", "https://ntfy.sh")
        set(v) = putStr("ntfyServer", v.trim())

    var ntfyTopic: String
        get() = str("ntfyTopic", "")
        set(v) = putStr("ntfyTopic", v.trim())

    var ntfyToken: String
        get() = str("ntfyToken", "")
        set(v) = putStr("ntfyToken", v.trim())

    // --- Orbita (webhook JSON) ---
    var webhookEnabled: Boolean
        get() = bool("webhookEnabled", false)
        set(v) = putBool("webhookEnabled", v)

    var webhookUrl: String
        get() = str("webhookUrl", "")
        set(v) = putStr("webhookUrl", v.trim())

    var webhookToken: String
        get() = str("webhookToken", "")
        set(v) = putStr("webhookToken", v.trim())

    /** Con Orbita attivo, la mail parte solo se Orbita non risponde */
    var mailFallbackOnly: Boolean
        get() = bool("mailFallbackOnly", true)
        set(v) = putBool("mailFallbackOnly", v)

    // Elenchi presi da Orbita (conti e tag), tenuti per usarli anche senza rete
    var orbitaConti: List<String>
        get() = str("orbitaConti", "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        set(v) = putStr("orbitaConti", v.joinToString("\n"))

    var orbitaMetodi: List<String>
        get() = str("orbitaMetodi", "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { DEFAULT_METODI }
        set(v) = putStr("orbitaMetodi", v.joinToString("\n"))

    var orbitaTag: List<String>
        get() = str("orbitaTag", "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        set(v) = putStr("orbitaTag", v.joinToString("\n"))

    // --- Messaggio ---
    /** Segnaposto: {app}, {title}, {text}, {pkg}, {date} */
    var subjectTemplate: String
        get() = str("subject", DEFAULT_SUBJECT)
        set(v) = putStr("subject", v)

    var bodyTemplate: String
        get() = str("body", DEFAULT_BODY)
        set(v) = putStr("body", v)

    var html: Boolean
        get() = bool("html", false)
        set(v) = putBool("html", v)

    var ignoreOngoing: Boolean
        get() = bool("ignoreOngoing", true)
        set(v) = putBool("ignoreOngoing", v)

    // --- Regole ---
    var rules: List<Rule>
        get() = Rule.listFromJson(str("rules", "[]"))
        set(v) = putStr("rules", Rule.listToJson(v))

    fun anyChannelEnabled(): Boolean = mailEnabled || tgEnabled || ntfyEnabled || webhookEnabled

    /**
     * Dalla versione 1 (elenco app + filtri globali) alle regole:
     * le impostazioni già inserite vengono convertite una volta sola.
     */
    private fun migrateIfNeeded() {
        if (prefs.contains("rules")) return
        val oldApps = prefs.getStringSet("apps", emptySet()) ?: emptySet()
        val all = prefs.getBoolean("allApps", false)
        val inc = str("include", "")
        val exc = str("exclude", "")
        val list = mutableListOf<Rule>()
        if (all) {
            list += Rule(pkg = Rule.ALL, appLabel = "Tutte le app", include = inc, exclude = exc)
        } else {
            for (p in oldApps) {
                list += Rule(pkg = p, appLabel = labelOf(p), include = inc, exclude = exc)
            }
        }
        prefs.edit().putString("rules", Rule.listToJson(list)).apply()
    }

    private fun labelOf(pkg: String): String = try {
        appCtx.packageManager.getApplicationLabel(appCtx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    companion object {
        /** Metodi di pagamento di Orbita, usati finché non si scarica l'elenco */
        val DEFAULT_METODI = listOf(
            "Contanti", "RID/SDD", "Bonifico", "C.Credito 1", "C.Credito 2", "C.Debito",
            "Contactless", "PayPal", "Bollettino", "Prepagata", "Assegno",
            "Revolut", "Apple Pay", "Google Pay", "Postepay", "Satispay",
            "Scalapay", "Amazon Pay", "Altro"
        )
        const val DEFAULT_SUBJECT = "[{app}] {title}"
        const val DEFAULT_BODY = "App: {app} ({pkg})\nData: {date}\n\n{title}\n{text}"
    }
}
