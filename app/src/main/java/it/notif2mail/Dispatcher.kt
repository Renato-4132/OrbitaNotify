package it.notif2mail

import javax.mail.AuthenticationFailedException
import javax.mail.SendFailedException
import javax.mail.internet.AddressException

/**
 * Manda un messaggio su tutti i canali attivi e dice com'è andata su ciascuno.
 */
object Dispatcher {

    data class Message(
        val subject: String,
        val text: String,
        val html: String?,
        /** Notifiche originali, per i canali che vogliono dati strutturati (Orbita) */
        val items: List<Db.Pending> = emptyList(),
        val ruleName: String = "",
        val digest: Boolean = false,
        /** Conto e tag della regola, per Orbita */
        val conto: String = "",
        val tags: List<String> = emptyList()
    )

    data class ChannelResult(
        val channel: String,
        val ok: Boolean,
        /** Ha senso riprovare più tardi (rete assente, server in errore)? */
        val retryable: Boolean,
        val error: String? = null,
        /** Errore già compensato da un altro canale (es. Orbita giù ma mail partita) */
        val soft: Boolean = false
    )

    fun send(cfg: Config, msg: Message, recipientsOverride: String?): List<ChannelResult> {
        val out = mutableListOf<ChannelResult>()

        // Orbita per primo: se risponde, la mail può restare a riposo
        val webhookActive = cfg.webhookEnabled && cfg.webhookUrl.isNotBlank()
        var orbitaIdx = -1
        var orbitaOk = false
        if (webhookActive) {
            val r = attempt("Orbita") { Webhook.send(cfg.webhookUrl, cfg.webhookToken, msg) }
            out += r
            orbitaIdx = out.lastIndex
            orbitaOk = r.ok
        }

        val recipients = recipientsOverride?.takeIf { it.isNotBlank() } ?: cfg.to
        val fallback = webhookActive && cfg.mailFallbackOnly
        val skipMail = fallback && orbitaOk
        if (!skipMail && cfg.mailEnabled && cfg.host.isNotBlank() && cfg.user.isNotBlank() &&
            cfg.password.isNotBlank() && recipients.isNotBlank()
        ) {
            val r = attempt(if (fallback) "Mail (ripiego)" else "Mail") {
                MailSender.send(cfg, msg.subject, msg.text, msg.html, recipients)
            }
            out += r
            if (orbitaIdx >= 0 && !orbitaOk && r.ok) out[orbitaIdx] = out[orbitaIdx].copy(soft = true)
        }
        if (cfg.tgEnabled && cfg.tgToken.isNotBlank() && cfg.tgChatId.isNotBlank()) {
            out += attempt("Telegram") {
                Telegram.send(cfg.tgToken, cfg.tgChatId, msg.subject + "\n\n" + msg.text)
            }
        }
        if (cfg.ntfyEnabled && cfg.ntfyTopic.isNotBlank()) {
            out += attempt("ntfy") {
                Ntfy.send(cfg.ntfyServer, cfg.ntfyTopic, cfg.ntfyToken, msg.subject, msg.text)
            }
        }
        return out
    }

    private fun attempt(name: String, block: () -> Unit): ChannelResult = try {
        block()
        ChannelResult(name, ok = true, retryable = false)
    } catch (e: HttpError) {
        ChannelResult(name, false, e.code >= 500 || e.code == 429, "HTTP ${e.code}: ${e.message}")
    } catch (e: AuthenticationFailedException) {
        ChannelResult(name, false, false, "Accesso rifiutato: ${e.message}")
    } catch (e: AddressException) {
        ChannelResult(name, false, false, "Indirizzo non valido: ${e.message}")
    } catch (e: SendFailedException) {
        ChannelResult(name, false, false, e.message ?: "Invio rifiutato")
    } catch (e: Exception) {
        ChannelResult(name, false, true, e.message ?: e.javaClass.simpleName)
    }
}
