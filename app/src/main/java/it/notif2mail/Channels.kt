package it.notif2mail

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpError(val code: Int, message: String) : Exception(message)

object Http {
    /** POST sincrono: chiamare da un thread in background. Lancia HttpError se il codice non è 2xx. */
    fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = 15000,
        readTimeoutMs: Int = 20000
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpError(code, text.take(200))
            return text
        } finally {
            conn.disconnect()
        }
    }
}

object HttpGet {
    /** GET sincrono: chiamare da un thread in background. Lancia HttpError se il codice non è 2xx. */
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = 8000,
        readTimeoutMs: Int = 10000
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpError(code, text.take(200))
            return text
        } finally {
            conn.disconnect()
        }
    }
}

object Telegram {
    fun send(token: String, chatId: String, text: String) {
        val body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
            "&text=" + URLEncoder.encode(text.take(4000), "UTF-8")
        Http.post(
            "https://api.telegram.org/bot$token/sendMessage",
            body,
            "application/x-www-form-urlencoded"
        )
    }
}

object Ntfy {
    fun send(server: String, topic: String, token: String, title: String, message: String) {
        val base = server.trim().trimEnd('/').ifBlank { "https://ntfy.sh" }
        val json = JSONObject()
            .put("topic", topic.trim())
            .put("title", title.take(250))
            .put("message", message.take(3500))
        val headers = if (token.isNotBlank()) mapOf("Authorization" to "Bearer ${token.trim()}") else emptyMap()
        Http.post(base, json.toString(), "application/json", headers)
    }
}

/** Manda un JSON a Orbita. Qualunque risposta diversa da 2xx conta come errore. */
object Webhook {
    data class Options(val conti: List<String>, val tag: List<String>, val metodi: List<String>)

    /** Chiede a Orbita l'elenco di conti, tag e metodi di pagamento, per le tendine della regola. */
    fun fetchOptions(url: String, token: String): Options {
        val headers = if (token.isNotBlank()) mapOf("Authorization" to "Bearer ${token.trim()}") else emptyMap()
        val o = JSONObject(HttpGet.get(url.trim().trimEnd('/') + "/opzioni", headers))
        fun list(key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
        }
        return Options(list("conti"), list("tag"), list("metodi"))
    }

    fun send(url: String, token: String, msg: Dispatcher.Message) {
        val items = JSONArray()
        msg.items.forEach {
            items.put(
                JSONObject()
                    .put("ts", it.ts)
                    .put("app", it.app)
                    .put("pkg", it.pkg)
                    .put("title", it.title)
                    .put("text", it.text)
            )
        }
        val body = JSONObject()
            .put("source", "notif2mail")
            .put("version", 1)
            .put("sent_at", System.currentTimeMillis())
            .put("rule", msg.ruleName)
            .put("digest", msg.digest)
            .put("conto", msg.conto)
            .put("metodo", msg.metodo)
            .put("tags", JSONArray(msg.tags))
            .put("items", items)
        val headers = if (token.isNotBlank()) mapOf("Authorization" to "Bearer ${token.trim()}") else emptyMap()
        Http.post(url.trim(), body.toString(), "application/json; charset=utf-8", headers, 8000, 10000)
    }
}
