package com.carmeloparrinelli.lira

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
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
                    "Sei LIRA, un'assistente personale. Rispondi in italiano, in modo naturale e conciso.\\nUtente: $prompt\\nLIRA:"
                )
                answer.trim().ifEmpty { error("Il modello non ha prodotto una risposta.") }
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
