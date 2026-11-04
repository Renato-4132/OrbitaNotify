package it.notif2mail

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object MailSender {

    /** Corpo HTML senza passare dai gestori MIME di JavaMail (che su Android danno problemi). */
    private class HtmlSource(private val html: String) : DataSource {
        override fun getInputStream(): InputStream = ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
        override fun getOutputStream(): OutputStream = throw IOException("sola lettura")
        override fun getContentType(): String = "text/html; charset=UTF-8"
        override fun getName(): String = "body"
    }

    /** Invio sincrono: chiamare sempre da un thread in background. */
    fun send(cfg: Config, subject: String, text: String, html: String?, recipients: String) {
        val props = Properties().apply {
            put("mail.smtp.host", cfg.host)
            put("mail.smtp.port", cfg.port.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "20000")
            put("mail.smtp.writetimeout", "20000")
            when (cfg.security) {
                "SSL" -> {
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.ssl.checkserveridentity", "true")
                }
                "STARTTLS" -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                    put("mail.smtp.ssl.checkserveridentity", "true")
                }
            }
        }

        val user = cfg.user
        val pass = cfg.password
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(user, pass)
        })

        val sender = cfg.from.ifBlank { cfg.user }
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(sender))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipients))
            setSubject(subject, "UTF-8")
            if (html != null) {
                setDataHandler(DataHandler(HtmlSource(html)))
            } else {
                setText(text, "UTF-8")
            }
        }
        Transport.send(message)
    }
}
