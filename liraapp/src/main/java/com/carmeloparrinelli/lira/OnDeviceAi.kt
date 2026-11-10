package com.carmeloparrinelli.lira

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * On-device LLM using a compatible MediaPipe .task model.
 * Import is explicit: model weights are never included in the APK.
 */
class OnDeviceAi(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private var engine: LlmInference? = null
    private val modelFile: File get() = File(context.filesDir, "lira-model.task")

    fun hasModel(): Boolean = modelFile.exists() && modelFile.length() > 1024 * 1024

    fun importModel(uri: Uri, callback: (Result<Long>) -> Unit) {
        executor.execute {
            val result = runCatching {
                engine?.close()
                engine = null
                val temporary = File(context.filesDir, "lira-model.tmp")
                try {
                    val source = context.contentResolver.openInputStream(uri)
                        ?: error("Impossibile leggere il modello selezionato.")
                    val bytes = source.use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    require(bytes > 1024 * 1024) { "Il file non sembra un modello valido." }
                    if (modelFile.exists()) modelFile.delete()
                    check(temporary.renameTo(modelFile)) { "Impossibile salvare il modello." }
                    bytes
                } finally {
                    temporary.delete()
                }
            }
            callback(result)
        }
    }

    /**
     * Downloads the public model artifact only when the provider grants access.
     * Gemma licensing may require explicit acceptance at Hugging Face.
     */
    fun downloadModel(url: String, progress: (Int) -> Unit, callback: (Result<Long>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val target = URL(url)
                require(target.protocol == "https") { "Il download richiede HTTPS." }
                val connection = target.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                val temp = File(context.filesDir, "lira-model.download")
                try {
                    val code = connection.responseCode
                    check(code == 200) {
                        if (code == 401 || code == 403) "Accesso al modello non autorizzato. Accetta prima la licenza sul sito del produttore."
                        else "Download non disponibile: HTTP $code"
                    }
                    val length = connection.contentLengthLong
                    var downloaded = 0L
                    connection.inputStream.use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                downloaded += n
                                if (length > 0) progress((downloaded * 100 / length).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                    check(downloaded > 100L * 1024 * 1024) { "Download incompleto o file non valido." }
                    engine?.close()
                    engine = null
                    if (modelFile.exists()) modelFile.delete()
                    check(temp.renameTo(modelFile)) { "Impossibile salvare il modello scaricato." }
                    downloaded
                } finally {
                    temp.delete()
                    connection.disconnect()
                }
            }
            callback(result)
        }
    }

    fun generate(prompt: String, callback: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                check(hasModel()) { "Importa prima un modello MediaPipe .task compatibile." }
                if (engine == null) {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(1024)
                        .setMaxTopK(40)
                        .build()
                    engine = LlmInference.createFromOptions(context, options)
                }
                val answer = engine!!.generateResponse(
                    "Sei LIRA, un'assistente personale. Rispondi in italiano, in modo naturale e conciso. Se non conosci una risposta, dillo senza inventare nomi o fatti.\nUtente: $prompt\nLIRA:"
                )
                answer.replace("\\n", "\n")
                    .replace("\\t", " ")
                    .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
                    .trim()
                    .ifEmpty { error("Il modello non ha prodotto una risposta.") }
            }
            callback(result)
        }
    }

    fun shutdown() {
        executor.execute {
            engine?.close()
            engine = null
        }
        executor.shutdown()
    }
}
