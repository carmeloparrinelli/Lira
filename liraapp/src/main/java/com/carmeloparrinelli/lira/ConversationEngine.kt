package com.carmeloparrinelli.lira

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline conversational fallback. This is a deterministic intent engine,
 * not a generative language model. It does not require API keys.
 */
class ConversationEngine(context: Context) {
    private val prefs = context.getSharedPreferences("lira_conversation", Context.MODE_PRIVATE)
    private var lastTopic: String = prefs.getString("last_topic", "") ?: ""

    fun answer(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return "Dimmi pure cosa vuoi fare."
        val normalized = normalize(text)

        val namePattern = Regex("(?:mi chiamo|il mio nome e|chiamami)\\s+([\\p{L}][\\p{L} '-]{0,35})", RegexOption.IGNORE_CASE)
        val nameMatch = namePattern.find(normalized)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1].trim().split(" ").take(2).joinToString(" ")
                .replaceFirstChar { it.titlecase(Locale.ITALIAN) }
            prefs.edit().putString("user_name", name).apply()
            return "Piacere, $name. Ricorderò come chiamarti."
        }
        if (normalized.contains("come mi chiamo") || normalized.contains("ricordi il mio nome")) {
            val name = prefs.getString("user_name", null)
            return if (name.isNullOrBlank()) "Non conosco ancora il tuo nome. Puoi dirmi: chiamami seguito dal nome."
                else "Ti chiami $name."
        }
        if (normalized.contains("dimentica il mio nome") || normalized.contains("cancella il mio nome")) {
            prefs.edit().remove("user_name").apply()
            return "Ho cancellato il nome che avevo memorizzato."
        }
        if (normalized.contains("che ore") || normalized == "ora" || normalized.contains("dimmi l ora")) {
            remember("time")
            return "Sono le " + SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date())
        }
        if (normalized.contains("che giorno") || normalized.contains("che data") || normalized == "data" || normalized.contains("data di oggi")) {
            remember("date")
            return "Oggi è " + SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN).format(Date())
        }
        if (normalized in setOf("e adesso", "e ora", "adesso", "ora invece", "e oggi")) {
            return when (lastTopic) {
                "time" -> "Adesso sono le " + SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date())
                "date" -> "Oggi è " + SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN).format(Date())
                else -> null
            }
        }
        if (normalized.contains("cosa ricordi di me")) {
            val name = prefs.getString("user_name", null)
            return if (name.isNullOrBlank()) "Non ho ancora informazioni personali memorizzate."
                else "Ricordo che preferisci essere chiamato $name."
        }
        if (normalized.contains("cancella memoria") || normalized.contains("dimentica tutto")) {
            prefs.edit().clear().apply()
            lastTopic = ""
            return "Ho cancellato le informazioni memorizzate dal modulo conversazionale."
        }
        if (normalized.contains("ciao") || normalized.contains("buongiorno") || normalized.contains("buonasera")) {
            val name = prefs.getString("user_name", null)
            return if (name.isNullOrBlank()) "Ciao! Come posso aiutarti?" else "Ciao, $name! Come posso aiutarti?"
        }
        if (normalized.contains("come stai")) return "Tutto pronto. Dimmi pure."
        if (normalized.contains("chi sei") || normalized.contains("come ti chiami")) return "Sono Lira, il tuo assistente Android."
        if (normalized.contains("grazie")) return "Prego!"
        if (normalized.contains("cosa sai fare") || normalized == "aiuto") {
            return "Posso aprire alcune impostazioni e applicazioni, dirti data e ora, ricordare il tuo nome e rispondere a semplici comandi. Le conversazioni libere con un modello AI non sono ancora disponibili."
        }
        return null
    }

    private fun remember(topic: String) {
        lastTopic = topic
        prefs.edit().putString("last_topic", topic).apply()
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[!?.,;:]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
