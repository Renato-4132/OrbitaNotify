package it.notif2mail

import android.text.TextUtils
import java.text.DateFormat
import java.util.Date

/** Costruisce oggetto e corpo dai modelli, in testo semplice o HTML. */
object Formatter {

    private val placeholder = Regex("\\{(app|pkg|title|text|date)\\}")

    private fun fill(template: String, p: Db.Pending): String =
        placeholder.replace(template) { m ->
            when (m.groupValues[1]) {
                "app" -> p.app
                "pkg" -> p.pkg
                "title" -> p.title
                "text" -> p.text
                else -> DateFormat.getDateTimeInstance().format(Date(p.ts))
            }
        }

    private fun subject(cfg: Config, p: Db.Pending): String =
        fill(cfg.subjectTemplate, p).replace(Regex("\\s+"), " ").trim().take(200)
            .ifBlank { "Notifica da ${p.app}" }

    private fun plain(cfg: Config, p: Db.Pending): String = fill(cfg.bodyTemplate, p).trim()

    private fun htmlFragment(text: String): String =
        TextUtils.htmlEncode(text).replace("\n", "<br>")

    private fun htmlPage(inner: String): String =
        "<html><body style=\"font-family:sans-serif\">$inner</body></html>"

    fun single(cfg: Config, p: Db.Pending, ruleName: String = ""): Dispatcher.Message {
        val text = plain(cfg, p)
        return Dispatcher.Message(
            subject(cfg, p),
            text,
            if (cfg.html) htmlPage(htmlFragment(text)) else null,
            listOf(p),
            ruleName
        )
    }

    fun digest(cfg: Config, items: List<Db.Pending>, ruleName: String = ""): Dispatcher.Message {
        val sorted = items.sortedBy { it.ts }
        val apps = sorted.map { it.app }.distinct()
        val subject = "Riepilogo: ${sorted.size} notifiche" +
            if (apps.size == 1) " da ${apps[0]}" else ""
        val bodies = sorted.map { plain(cfg, it) }
        val text = bodies.joinToString("\n\n----------\n\n")
        val html = if (cfg.html) htmlPage(bodies.joinToString("<hr>") { htmlFragment(it) }) else null
        return Dispatcher.Message(subject, text, html, sorted, ruleName, digest = true)
    }

    fun test(cfg: Config): Dispatcher.Message = single(
        cfg,
        Db.Pending(
            0, "", System.currentTimeMillis(), "OrbitaNotify", "it.notif2mail",
            "Notifica di prova", "Se leggi questo messaggio, il canale funziona."
        )
    )
}
