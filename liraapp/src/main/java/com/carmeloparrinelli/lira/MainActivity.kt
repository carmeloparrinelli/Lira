package com.carmeloparrinelli.lira

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
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

private val cyan = Color(0xFF64D9FF)
private val navy = Color(0xFF070E1B)
private val panel = Color(0xFF122338)

class MainActivity : ComponentActivity() {
    private fun runCommand(command: String): String {
        val c = command.trim().lowercase(Locale.ITALIAN)
        if (c.isBlank()) return "Inserisci un comando."
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
        setContent {
            var command by remember { mutableStateOf("") }
            var response by remember { mutableStateOf("Sistema pronto. Puoi scrivere o dettare un comando.") }
            var selectedTab by remember { mutableStateOf(0) }
            val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    command = spoken
                    response = runCommand(spoken)
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
                        Text("● ONLINE", fontSize = 11.sp, color = Color(0xFF80E5C0))
                    }
                    HorizontalDivider(color = Color(0xFF26415C))
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                            Button(onClick = { response = runCommand(command) }, modifier = Modifier.weight(1f)) {
                                Text("ESEGUI")
                            }
                            OutlinedButton(onClick = {
                                try {
                                    recognizer.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Parla con LIRA")
                                    })
                                } catch (_: ActivityNotFoundException) {
                                    response = "Riconoscimento vocale non disponibile sul dispositivo."
                                }
                            }, modifier = Modifier.weight(1f)) {
                                Text("MICROFONO")
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("COMANDI DISPONIBILI", fontSize = 11.sp, color = Color(0xFF9CB8CE))
                        Spacer(Modifier.height(10.dp))
                        listOf("Apri impostazioni", "Apri Wi-Fi", "Apri Bluetooth", "Che ore sono?", "Che giorno è oggi?").forEach { example ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    command = example
                                    response = runCommand(example)
                                },
                                color = panel, shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(example, Modifier.padding(12.dp), color = Color(0xFFD8E9F5), fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                    HorizontalDivider(color = Color(0xFF26415C))
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("HOME", "ATTIVITÀ", "IMPOSTAZIONI").forEachIndexed { index, label ->
                            Text(label, color = if (selectedTab == index) cyan else Color(0xFF829BAF),
                                fontSize = 11.sp, modifier = Modifier.clickable {
                                    selectedTab = index
                                    when (index) {
                                        0 -> response = "Sistema pronto."
                                        1 -> response = "Le automazioni avanzate saranno disponibili nei prossimi aggiornamenti."
                                        2 -> response = runCommand("apri impostazioni")
                                    }
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
