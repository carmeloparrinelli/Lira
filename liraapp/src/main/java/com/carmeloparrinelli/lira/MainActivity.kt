package com.carmeloparrinelli.lira

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import androidx.core.content.ContextCompat
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.content.Context
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private val cyan = Color(0xFF64D9FF)
private val navy = Color(0xFF070E1B)
private val panel = Color(0xFF122338)

class MainActivity : ComponentActivity() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ready = false
    private var voiceEnabled = true
    private var voiceRate = 1.05f
    private var voicePitch = 0.95f
    private fun speak(message: String) {
        if (voiceEnabled && ready) {
            tts?.setSpeechRate(voiceRate)
            tts?.setPitch(voicePitch)
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "lira")
        }
    }
    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun runCommand(command: String): String {
        val c = command.trim().lowercase(Locale.ITALIAN)
        if (c.isBlank()) return "Inserisci un comando."
        if (c.contains("ciao") || c.contains("buongiorno") || c.contains("buonasera")) return "Ciao! Sono Lira. Come posso aiutarti?"
        if (c.contains("come stai")) return "Sono pronta ad aiutarti."
        if (c.contains("chi sei") || c.contains("come ti chiami")) return "Sono Lira, il tuo assistente Android."
        if (c.contains("grazie")) return "Prego! Posso fare altro?"
        if (c.contains("cosa sai fare") || c.contains("aiuto")) return "Posso aprire impostazioni, Wi-Fi, Bluetooth e browser e dirti ora e data."
        val action = when {
            c.contains("impostazioni") -> Intent(Settings.ACTION_SETTINGS)
            c.contains("wifi") || c.contains("wi-fi") -> Intent(Settings.ACTION_WIFI_SETTINGS)
            c.contains("bluetooth") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            c.contains("fotocamera") || c.contains("camera") -> Intent("android.media.action.IMAGE_CAPTURE")
            c.contains("browser") || c.contains("internet") -> Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
            else -> null
        }
        if (action != null) return try {
            startActivity(action)
            "Comando eseguito: ${command.trim()}"
        } catch (_: Exception) {
            "Impossibile aprire l'app richiesta su questo dispositivo."
        }
        if (c.contains("che ore") || c == "ora") {
            return "Sono le " + SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date())
        }
        if (c.contains("data") || c.contains("giorno")) {
            return "Oggi è " + SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN).format(Date())
        }
        return "Comando ricevuto. Questa funzione sarà disponibile con i prossimi moduli di LIRA."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("lira_voice", Context.MODE_PRIVATE)
        voiceEnabled = prefs.getBoolean("enabled", true)
        voiceRate = prefs.getFloat("rate", 1.05f)
        voicePitch = prefs.getFloat("pitch", 0.95f)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.ITALIAN)
                ready = result != null && result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        setContent {
            var command by remember { mutableStateOf("") }
            var response by remember { mutableStateOf("Sistema pronto. Puoi scrivere o dettare un comando.") }
            var selectedTab by remember { mutableStateOf(0) }
            var voiceOn by remember { mutableStateOf(voiceEnabled) }
            var rate by remember { mutableFloatStateOf(voiceRate) }
            var pitch by remember { mutableFloatStateOf(voicePitch) }
            val history = remember { mutableStateListOf<Pair<String, String>>() }
            LaunchedEffect(Unit) {
                try {
                    val saved = JSONArray(prefs.getString("history", "[]"))
                    for (i in 0 until saved.length()) {
                        val item = saved.getJSONObject(i)
                        history.add(item.optString("question") to item.optString("answer"))
                    }
                } catch (_: Exception) {}
            }
            fun submit(text: String) {
                val answer = runCommand(text)
                response = answer
                if (text.isNotBlank()) {
                    history.add(0, text.trim() to answer)
                    if (history.size > 50) history.removeAt(history.lastIndex)
                    val saved = JSONArray()
                    history.forEach { (q, a) ->
                        saved.put(JSONObject().put("question", q).put("answer", a))
                    }
                    prefs.edit().putString("history", saved.toString()).apply()
                }
                speak(answer)
            }
            var listening by remember { mutableStateOf(false) }
            val speechPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    response = "Microfono autorizzato. Tocca nuovamente MICROFONO."
                } else {
                    response = "Per ascoltare serve il permesso del microfono."
                }
            }
            DisposableEffect(Unit) {
                if (SpeechRecognizer.isRecognitionAvailable(this@MainActivity)) {
                    val recognizer = SpeechRecognizer.createSpeechRecognizer(this@MainActivity)
                    speechRecognizer = recognizer
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) { listening = true }
                        override fun onBeginningOfSpeech() { listening = true }
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() { listening = false }
                        override fun onError(error: Int) {
                            listening = false
                            response = "Ascolto non riuscito (codice " + error + "). Riprova."
                        }
                        override fun onResults(results: Bundle?) {
                            listening = false
                            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            if (!spoken.isNullOrBlank()) {
                                command = spoken
                                submit(spoken)
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            if (!partial.isNullOrBlank()) command = partial
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                } else {
                    response = "Riconoscimento vocale non disponibile."
                }
                onDispose {
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                }
            }
            val pulse = rememberInfiniteTransition(label = "pulse")
            val scale by pulse.animateFloat(
                initialValue = 0.94f, targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "core"
            )
            MaterialTheme(colorScheme = darkColorScheme(primary = cyan, background = navy, surface = panel)) {
                Column(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(navy, Color(0xFF0A1A30), navy))
                    ).systemBarsPadding()
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("L I R A", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cyan)
                            Text("ASSISTENTE PERSONALE", fontSize = 10.sp, color = Color(0xFF9CB8CE))
                        }
                        Text("● PRONTA", fontSize = 11.sp, color = Color(0xFF80E5C0))
                    }
                    Divider(color = Color(0xFF26415C))
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (selectedTab == 0) {
                        Spacer(Modifier.height(18.dp))
                        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(178.dp).scale(scale).border(1.dp, cyan.copy(alpha = .35f), CircleShape))
                            Box(Modifier.size(145.dp).border(2.dp, cyan.copy(alpha = .7f), CircleShape))
                            Box(Modifier.size(116.dp).background(
                                Brush.radialGradient(listOf(Color(0xFF2D9DCA), Color(0xFF102C50), navy)),
                                CircleShape
                            ).border(2.dp, cyan, CircleShape))
                            Text("L", fontSize = 58.sp, fontWeight = FontWeight.Light, color = Color.White)
                        }
                        Text("PRONTA AD ASCOLTARTI", color = cyan, fontSize = 13.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(18.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = panel),
                            border = BorderStroke(1.dp, Color(0xFF2A4A66)),
                            shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("RISPOSTA DI LIRA", color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text(response, color = Color(0xFFE2F1FC), fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = command, onValueChange = { command = it },
                            label = { Text("Scrivi un comando") },
                            placeholder = { Text("Es. Apri impostazioni") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { submit(command) }, modifier = Modifier.weight(1f)) {
                                Text("ESEGUI")
                            }
                            OutlinedButton(onClick = {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                                    != PackageManager.PERMISSION_GRANTED) {
                                    speechPermission.launch(Manifest.permission.RECORD_AUDIO)
                                } else if (listening) {
                                    speechRecognizer?.stopListening()
                                    listening = false
                                } else {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    }
                                    speechRecognizer?.startListening(intent)
                                    listening = true
                                    response = "Ti ascolto..."
                                }
                            }, modifier = Modifier.weight(1f)) {
                                Text(if (listening) "FERMA" else "MICROFONO")
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("COMANDI DISPONIBILI", fontSize = 11.sp, color = Color(0xFF9CB8CE))
                        Spacer(Modifier.height(10.dp))
                        listOf("Apri impostazioni", "Apri Wi-Fi", "Apri Bluetooth", "Che ore sono?", "Che giorno è oggi?").forEach { example ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    command = example
                                    submit(example)
                                },
                                color = panel, shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(example, Modifier.padding(12.dp), color = Color(0xFFD8E9F5), fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        } else if (selectedTab == 1) {
                            Spacer(Modifier.height(24.dp))
                            Text("CRONOLOGIA", color = cyan, fontSize = 18.sp)
                            Spacer(Modifier.height(16.dp))
                            if (history.isEmpty()) Text("Nessun comando in questa sessione.", color = Color.LightGray)
                            history.forEach { (question, answer) ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    colors = CardDefaults.cardColors(containerColor = panel)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(question, color = cyan, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(6.dp))
                                        Text(answer, color = Color.White)
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(24.dp))
                            Text("IMPOSTAZIONI VOCALI", color = cyan, fontSize = 18.sp)
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Risposte parlate", modifier = Modifier.weight(1f), color = Color.White)
                                Switch(checked = voiceOn, onCheckedChange = {
                                    voiceOn = it
                                    voiceEnabled = it
                                    prefs.edit().putBoolean("enabled", it).apply()
                                    if (!it) tts?.stop()
                                })
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Velocità della voce", color = Color.White)
                            Slider(value = rate, onValueChange = {
                                rate = it
                                voiceRate = it
                                prefs.edit().putFloat("rate", it).apply()
                            }, valueRange = 0.7f..1.4f)
                            Spacer(Modifier.height(12.dp))
                            Text("Tonalità della voce", color = Color.White)
                            Slider(value = pitch, onValueChange = {
                                pitch = it
                                voicePitch = it
                                prefs.edit().putFloat("pitch", it).apply()
                            }, valueRange = 0.75f..1.25f)
                            OutlinedButton(onClick = { speak("Ciao, sono Lira. Questa è la mia voce.") }) {
                                Text("PROVA VOCE")
                            }
                            Text("Voce italiana del motore Android installato.", color = Color.LightGray)
                        }
                    }
                    Divider(color = Color(0xFF26415C))
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("HOME", "ATTIVITÀ", "IMPOSTAZIONI").forEachIndexed { index, label ->
                            Text(label, color = if (selectedTab == index) cyan else Color(0xFF829BAF),
                                fontSize = 11.sp, modifier = Modifier.clickable {
                                    selectedTab = index

                                }.padding(8.dp))
                        }
                    }
                    Text("By Carmelo Parrinelli  ·  LIRA 1.0",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        textAlign = TextAlign.Center, fontSize = 10.sp, color = Color(0xFF6D859A))
                }
            }
        }
    }
}
