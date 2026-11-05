package it.notif2mail

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.DateFormat
import java.util.Date
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var cfg: Config
    private lateinit var db: Db

    private val securityOptions = listOf("SSL", "STARTTLS", "NONE")

    private val pages by lazy {
        mapOf(
            R.id.nav_status to R.id.pageStatus,
            R.id.nav_channels to R.id.pageChannels,
            R.id.nav_rules to R.id.pageRules,
            R.id.nav_message to R.id.pageMessage,
            R.id.nav_history to R.id.pageHistory
        )
    }

    private fun txt(id: Int): String = findViewById<TextView>(id).text.toString()
    private fun put(id: Int, s: String) {
        findViewById<TextView>(id).text = s
    }
    private fun sw(id: Int): MaterialSwitch = findViewById(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cfg = Config(this)
        db = Db.get(this)

        setupStatusPage()
        setupChannelsPage()
        setupOrbitaScan()
        setupRulesPage()
        setupHistoryPage()
        loadFields()

        val nav: BottomNavigationView = findViewById(R.id.nav)
        nav.setOnItemSelectedListener { item ->
            saveAll()
            showPage(item.itemId)
            true
        }
        showPage(R.id.nav_status)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        renderRules()
        renderHistory()
    }

    override fun onPause() {
        super.onPause()
        saveAll()
    }

    private fun showPage(navId: Int) {
        pages.forEach { (nav, page) ->
            findViewById<View>(page).visibility = if (nav == navId) View.VISIBLE else View.GONE
        }
        when (navId) {
            R.id.nav_status -> refreshStatus()
            R.id.nav_rules -> renderRules()
            R.id.nav_history -> renderHistory()
        }
    }

    // ---------------------------------------------------------------- Stato

    private fun setupStatusPage() {
        sw(R.id.swEnabled).setOnCheckedChangeListener { _, checked -> cfg.enabled = checked }

        findViewById<MaterialButton>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener { sendTest() }
    }

    private fun refreshStatus() {
        sw(R.id.swEnabled).isChecked = cfg.enabled

        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        val granted = listeners.contains(packageName)
        put(
            R.id.tvAccess,
            if (granted) "Accesso alle notifiche: concesso"
            else "Accesso alle notifiche: NON concesso (serve per funzionare)"
        )
        findViewById<MaterialButton>(R.id.btnAccess).alpha = if (granted) 0.6f else 1f

        val channels = listOfNotNull(
            if (cfg.mailEnabled) "Mail" else null,
            if (cfg.tgEnabled) "Telegram" else null,
            if (cfg.ntfyEnabled) "ntfy" else null,
            if (cfg.webhookEnabled) "Orbita" else null
        )
        val activeRules = cfg.rules.count { it.enabled }
        var summary = "Canali: ${channels.joinToString().ifEmpty { "nessuno" }}  ·  Regole attive: $activeRules"
        if (activeRules == 0) summary += "\nNessuna regola attiva: aggiungine una nella scheda Regole."
        put(R.id.tvSummary, summary)

        val last = db.history(1).firstOrNull()
        put(
            R.id.tvLast,
            if (last == null) "Ultimo invio: nessuno"
            else "Ultimo invio: ${fmtDate(last.ts)} — ${if (last.ok) "riuscito" else "con errori (vedi Cronologia)"}"
        )
    }

    private fun sendTest() {
        saveAll()
        val btn = findViewById<MaterialButton>(R.id.btnTest)
        btn.isEnabled = false
        thread {
            val results = Dispatcher.send(cfg, Formatter.test(cfg), null)
            runOnUiThread {
                btn.isEnabled = true
                if (isFinishing || isDestroyed) return@runOnUiThread
                val message = if (results.isEmpty()) {
                    "Nessun canale pronto. Attiva un canale e compila i suoi dati nella scheda Canali."
                } else {
                    results.joinToString("\n") { "${it.channel}: ${if (it.ok) "inviato" else it.error}" }
                }
                MaterialAlertDialogBuilder(this)
                    .setTitle("Risultato della prova")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    // --------------------------------------------------------------- Canali

    private fun setupChannelsPage() {
        val tabs: TabLayout = findViewById(R.id.tabsChannels)
        listOf("Mail", "Telegram", "ntfy", "Orbita").forEach { tabs.addTab(tabs.newTab().setText(it)) }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position ?: 0
                findViewById<View>(R.id.panelMail).visibility = if (pos == 0) View.VISIBLE else View.GONE
                findViewById<View>(R.id.panelTelegram).visibility = if (pos == 1) View.VISIBLE else View.GONE
                findViewById<View>(R.id.panelNtfy).visibility = if (pos == 2) View.VISIBLE else View.GONE
                findViewById<View>(R.id.panelOrbita).visibility = if (pos == 3) View.VISIBLE else View.GONE
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        val dd: MaterialAutoCompleteTextView = findViewById(R.id.ddSecurity)
        dd.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, securityOptions))
    }

    private fun setupOrbitaScan() {
        findViewById<View>(R.id.btnScanOrbitaUrl).setOnClickListener {
            scanQr { v ->
                if (v.startsWith("http://", true) || v.startsWith("https://", true)) {
                    put(R.id.etOrbitaUrl, v)
                } else {
                    toast("Il QR non contiene un indirizzo (https://...)")
                }
            }
        }
        findViewById<View>(R.id.btnScanOrbitaToken).setOnClickListener {
            scanQr { v -> put(R.id.etOrbitaToken, v) }
        }
    }

    private fun scanQr(onResult: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { b ->
                val v = b.rawValue?.trim().orEmpty()
                if (v.isNotEmpty()) onResult(v) else toast("QR vuoto")
            }
            .addOnFailureListener { e -> toast("Scansione non riuscita: ${e.message}") }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun loadFields() {
        // Mail
        sw(R.id.swMail).isChecked = cfg.mailEnabled
        put(R.id.etHost, cfg.host)
        put(R.id.etPort, cfg.port.toString())
        findViewById<MaterialAutoCompleteTextView>(R.id.ddSecurity).setText(cfg.security, false)
        put(R.id.etUser, cfg.user)
        put(R.id.etPassword, cfg.password)
        put(R.id.etTo, cfg.to)
        // Telegram
        sw(R.id.swTg).isChecked = cfg.tgEnabled
        put(R.id.etTgToken, cfg.tgToken)
        put(R.id.etTgChat, cfg.tgChatId)
        // ntfy
        sw(R.id.swNtfy).isChecked = cfg.ntfyEnabled
        put(R.id.etNtfyServer, cfg.ntfyServer)
        put(R.id.etNtfyTopic, cfg.ntfyTopic)
        put(R.id.etNtfyToken, cfg.ntfyToken)
        // Orbita
        sw(R.id.swOrbita).isChecked = cfg.webhookEnabled
        put(R.id.etOrbitaUrl, cfg.webhookUrl)
        put(R.id.etOrbitaToken, cfg.webhookToken)
        sw(R.id.swMailFallback).isChecked = cfg.mailFallbackOnly
        // Messaggio
        put(R.id.etSubject, cfg.subjectTemplate)
        put(R.id.etBody, cfg.bodyTemplate)
        sw(R.id.swHtml).isChecked = cfg.html
        sw(R.id.swOngoing).isChecked = cfg.ignoreOngoing
    }

    /** Salva i campi delle schede Canali e Messaggio (le regole si salvano a parte). */
    private fun saveAll() {
        cfg.mailEnabled = sw(R.id.swMail).isChecked
        cfg.host = txt(R.id.etHost)
        cfg.port = txt(R.id.etPort).trim().toIntOrNull() ?: 465
        val security = txt(R.id.ddSecurity).trim()
        cfg.security = if (security in securityOptions) security else "SSL"
        cfg.user = txt(R.id.etUser)
        cfg.password = txt(R.id.etPassword)
        cfg.to = txt(R.id.etTo)

        cfg.tgEnabled = sw(R.id.swTg).isChecked
        cfg.tgToken = txt(R.id.etTgToken)
        cfg.tgChatId = txt(R.id.etTgChat)

        cfg.ntfyEnabled = sw(R.id.swNtfy).isChecked
        cfg.ntfyServer = txt(R.id.etNtfyServer).ifBlank { "https://ntfy.sh" }
        cfg.ntfyTopic = txt(R.id.etNtfyTopic)
        cfg.ntfyToken = txt(R.id.etNtfyToken)

        cfg.webhookEnabled = sw(R.id.swOrbita).isChecked
        cfg.webhookUrl = txt(R.id.etOrbitaUrl)
        cfg.webhookToken = txt(R.id.etOrbitaToken)
        cfg.mailFallbackOnly = sw(R.id.swMailFallback).isChecked

        cfg.subjectTemplate = txt(R.id.etSubject).ifBlank { Config.DEFAULT_SUBJECT }
        cfg.bodyTemplate = txt(R.id.etBody).ifBlank { Config.DEFAULT_BODY }
        cfg.html = sw(R.id.swHtml).isChecked
        cfg.ignoreOngoing = sw(R.id.swOngoing).isChecked
    }

    // --------------------------------------------------------------- Regole

    private fun setupRulesPage() {
        findViewById<MaterialButton>(R.id.btnAddRule).setOnClickListener {
            startActivity(Intent(this, RuleEditActivity::class.java))
        }
    }

    private fun renderRules() {
        val list: LinearLayout = findViewById(R.id.rulesList)
        list.removeAllViews()
        val rules = cfg.rules
        findViewById<View>(R.id.tvRulesEmpty).visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE

        for (rule in rules) {
            val card = layoutInflater.inflate(R.layout.item_rule, list, false) as MaterialCardView
            card.findViewById<TextView>(R.id.tvRuleTitle).text = rule.appLabel
            card.findViewById<TextView>(R.id.tvRuleSub).text = rule.summary()
            val toggle = card.findViewById<MaterialSwitch>(R.id.swRule)
            toggle.isChecked = rule.enabled
            toggle.setOnCheckedChangeListener { _, checked ->
                cfg.rules = cfg.rules.map { if (it.id == rule.id) it.copy(enabled = checked) else it }
            }
            card.setOnClickListener {
                startActivity(Intent(this, RuleEditActivity::class.java).putExtra("id", rule.id))
            }
            list.addView(card)
        }
    }

    // ----------------------------------------------------------- Cronologia

    private fun setupHistoryPage() {
        findViewById<MaterialButton>(R.id.btnClearHistory).setOnClickListener {
            db.clearHistory()
            renderHistory()
            refreshStatus()
        }
    }

    private fun renderHistory() {
        val list: LinearLayout = findViewById(R.id.historyList)
        list.removeAllViews()
        val rows = db.history(100)
        findViewById<View>(R.id.tvHistoryEmpty).visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        for (h in rows) {
            val row = layoutInflater.inflate(R.layout.item_history, list, false)
            row.findViewById<TextView>(R.id.tvHistTitle).text = (if (h.ok) "✓ " else "✕ ") + h.subject
            row.findViewById<TextView>(R.id.tvHistMeta).text = "${fmtDate(h.ts)} · ${h.rule}"
            row.findViewById<TextView>(R.id.tvHistDetail).text = h.detail
            list.addView(row)
        }
    }

    private fun fmtDate(ts: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ts))
}
