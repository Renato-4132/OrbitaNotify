package it.notif2mail

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class RuleEditActivity : AppCompatActivity() {

    private lateinit var cfg: Config
    private var rule = Rule()
    private var isNew = true

    private var pkg = Rule.ALL
    private var appLabel = "Tutte le app"
    private var startMin = 8 * 60
    private var endMin = 20 * 60

    /** Chiave inviata a Orbita e testo mostrato nella tendina */
    private val autoOptions = listOf(
        "" to "No, da confermare a mano",
        "regole" to "Sì, se riconosciuta con le regole fisse",
        "tutto" to "Sì, sempre (anche con Gemini)"
    )

    private val chipIds = listOf(
        R.id.chipD0, R.id.chipD1, R.id.chipD2, R.id.chipD3, R.id.chipD4, R.id.chipD5, R.id.chipD6
    )

    private fun txt(id: Int): String = findViewById<TextView>(id).text.toString()
    private fun put(id: Int, s: String) {
        findViewById<TextView>(id).text = s
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_edit)
        cfg = Config(this)

        val id = intent.getStringExtra("id")
        cfg.rules.firstOrNull { it.id == id }?.let {
            rule = it
            isNew = false
        }
        pkg = rule.pkg
        appLabel = rule.appLabel
        startMin = rule.startMin
        endMin = rule.endMin

        put(R.id.tvEditTitle, if (isNew) "Nuova regola" else "Modifica regola")
        put(R.id.etInclude, rule.include)
        put(R.id.etExclude, rule.exclude)
        put(R.id.etRecipients, rule.recipients)
        put(R.id.etDigest, rule.digestMinutes.toString())
        put(R.id.etConto, rule.conto)
        put(R.id.etMetodo, rule.metodo)
        findViewById<AutoCompleteTextView>(R.id.etAuto).apply {
            setAdapter(ArrayAdapter(this@RuleEditActivity, android.R.layout.simple_list_item_1, autoOptions.map { it.second }))
            setText(autoOptions.firstOrNull { it.first == rule.auto }?.second ?: autoOptions[0].second, false)
        }
        put(R.id.etTag, rule.tag)
        refreshOptions()
        chipIds.forEachIndexed { i, chipId ->
            findViewById<Chip>(chipId).isChecked = rule.days and (1 shl i) != 0
        }

        val swWindow: MaterialSwitch = findViewById(R.id.swWindow)
        swWindow.isChecked = rule.useWindow
        swWindow.setOnCheckedChangeListener { _, checked -> updateWindowRow(checked) }
        updateWindowRow(rule.useWindow)

        findViewById<MaterialSwitch>(R.id.swRuleOn).isChecked = rule.enabled
        findViewById<MaterialButton>(R.id.btnDelete).visibility = if (isNew) View.GONE else View.VISIBLE

        refreshAppButton()
        refreshTimeButtons()

        findViewById<MaterialButton>(R.id.btnApp).setOnClickListener { pickApp() }
        findViewById<MaterialButton>(R.id.btnStart).setOnClickListener {
            pickTime(startMin) { startMin = it; refreshTimeButtons() }
        }
        findViewById<MaterialButton>(R.id.btnEnd).setOnClickListener {
            pickTime(endMin) { endMin = it; refreshTimeButtons() }
        }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener { confirmDelete() }
    }

    private fun fillDropdowns(conti: List<String>, tag: List<String>, metodi: List<String>) {
        fun adapter(items: List<String>) = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        findViewById<AutoCompleteTextView>(R.id.etConto).setAdapter(adapter(conti))
        findViewById<AutoCompleteTextView>(R.id.etMetodo).setAdapter(adapter(metodi))
        findViewById<AutoCompleteTextView>(R.id.etTag).setAdapter(adapter(tag))
    }

    /** Mostra gli elenchi già salvati e li aggiorna da Orbita, se risponde. */
    private fun refreshOptions() {
        fillDropdowns(cfg.orbitaConti, cfg.orbitaTag, cfg.orbitaMetodi)
        if (!cfg.webhookEnabled || cfg.webhookUrl.isBlank()) return
        val url = cfg.webhookUrl
        val token = cfg.webhookToken
        Thread {
            try {
                val o = Webhook.fetchOptions(url, token)
                cfg.orbitaConti = o.conti
                cfg.orbitaTag = o.tag
                if (o.metodi.isNotEmpty()) cfg.orbitaMetodi = o.metodi
                runOnUiThread { if (!isFinishing) fillDropdowns(o.conti, o.tag, cfg.orbitaMetodi) }
            } catch (e: Exception) {
                // Senza rete restano gli elenchi salvati; si può scrivere anche a mano
            }
        }.start()
    }

    private fun updateWindowRow(enabled: Boolean) {
        findViewById<View>(R.id.rowWindow).visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun refreshAppButton() {
        findViewById<MaterialButton>(R.id.btnApp).text = "App: $appLabel"
    }

    private fun refreshTimeButtons() {
        findViewById<MaterialButton>(R.id.btnStart).text = "Dalle ${Rule.fmtTime(startMin)}"
        findViewById<MaterialButton>(R.id.btnEnd).text = "Alle ${Rule.fmtTime(endMin)}"
    }

    private fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    private fun pickApp() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val all = listOf(Rule.ALL to "Tutte le app") + apps

        MaterialAlertDialogBuilder(this)
            .setTitle("Scegli l'app")
            .setItems(all.map { it.second }.toTypedArray()) { _, which ->
                pkg = all[which].first
                appLabel = all[which].second
                refreshAppButton()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun save() {
        var days = 0
        chipIds.forEachIndexed { i, chipId ->
            if (findViewById<Chip>(chipId).isChecked) days = days or (1 shl i)
        }
        if (days == 0) {
            Toast.makeText(this, "Seleziona almeno un giorno", Toast.LENGTH_SHORT).show()
            return
        }
        val digest = (txt(R.id.etDigest).trim().toIntOrNull() ?: 0).coerceIn(0, 1440)

        val updated = rule.copy(
            pkg = pkg,
            appLabel = appLabel,
            include = txt(R.id.etInclude).trim(),
            exclude = txt(R.id.etExclude).trim(),
            recipients = txt(R.id.etRecipients).trim(),
            days = days,
            useWindow = findViewById<MaterialSwitch>(R.id.swWindow).isChecked,
            startMin = startMin,
            endMin = endMin,
            digestMinutes = digest,
            conto = txt(R.id.etConto).trim(),
            metodo = txt(R.id.etMetodo).trim(),
            auto = autoOptions.firstOrNull { it.second == txt(R.id.etAuto) }?.first ?: "",
            tag = txt(R.id.etTag).trim().trimStart('#'),
            enabled = findViewById<MaterialSwitch>(R.id.swRuleOn).isChecked
        )

        val list = cfg.rules.toMutableList()
        val index = list.indexOfFirst { it.id == updated.id }
        if (index >= 0) list[index] = updated else list.add(updated)
        cfg.rules = list
        finish()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminare la regola?")
            .setMessage("Le notifiche di questa regola ancora in attesa di invio verranno scartate.")
            .setPositiveButton("Elimina") { _, _ ->
                cfg.rules = cfg.rules.filter { it.id != rule.id }
                Db.get(this).deletePendingForRule(rule.id)
                WorkManager.getInstance(this).cancelUniqueWork(Scheduler.uniqueName(rule.id))
                finish()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
