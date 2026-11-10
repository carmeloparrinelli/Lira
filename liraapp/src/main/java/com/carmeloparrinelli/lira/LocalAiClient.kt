package com.carmeloparrinelli.lira

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional AI bridge for a self-hosted Ollama server on the local network.
 * No API key or external cloud account is required.
 * Network calls must run off the Android main thread.
 */
class LocalAiClient {
    fun chat(endpoint: String, model: String, messages: List<Pair<String, String>>): String {
        val base = endpoint.trim().trimEnd('/')
        val url = URL("$base/api/chat")
        require(url.protocol == "http" || url.protocol == "https") { "Indirizzo non valido." }
        require(model.isNotBlank()) { "Inserisci il nome del modello." }
        val payload = JSONObject()
            .put("model", model.trim())
            .put("stream", false)
        val conversation = JSONArray()
        conversation.put(JSONObject().put("role", "system").put("content",
            "Sei LIRA, assistente personale Android. Rispondi in italiano, con chiarezza e precisione. " +
            "Non dichiarare di aver eseguito azioni sul dispositivo se non sono state eseguite."))
        messages.takeLast(12).forEach { (role, text) ->
            conversation.put(JSONObject().put("role", role).put("content", text))
        }
        payload.put("messages", conversation)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 6000
            connection.readTimeout = 90000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText().take(300) } ?: ""
                throw IllegalStateException("Server AI: HTTP $status. $detail")
            }
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            return response.optJSONObject("message")?.optString("content")?.trim()
                ?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Il modello non ha restituito testo.")
        } finally {
            connection.disconnect()
        }
    }
}
