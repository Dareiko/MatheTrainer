package dcc.mathe

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MatematickyTrener()
                }
            }
        }
    }
}

@Composable
fun MatematickyTrener() {
    var hraBezi by remember { mutableStateOf(false) }
    var limitPrikladov by remember { mutableIntStateOf(5) }
    
    var cislo1 by remember { mutableIntStateOf((1..12).random()) }
    var cislo2 by remember { mutableIntStateOf((1..12).random()) }
    var vstupPouzivatela by remember { mutableStateOf("") }
    var pocetPrikladov by remember { mutableIntStateOf(0) }
    var spravneOdpovede by remember { mutableIntStateOf(0) }
    
    var startTimeCelkovo by remember { mutableLongStateOf(0L) }
    var startTimePriklad by remember { mutableLongStateOf(0L) }
    var aktualneSekundy by remember { mutableIntStateOf(0) }
    var celkovyCas by remember { mutableLongStateOf(0L) }
    
    var zobrazVysledok by remember { mutableStateOf(false) }
    var farbaPozadia by remember { mutableStateOf(Color.Transparent) }
    val animovanaFarba by animateColorAsState(targetValue = farbaPozadia, label = "")
    val scope = rememberCoroutineScope()

    val jazyky = listOf("Slovenčina", "Deutsch")
    var zvolenyJazyk by remember { mutableStateOf(jazyky[0]) }

    // Pomocná funkcia na spracovanie odpovede (použitá pri tlačidle aj hlase)
    fun spracujOdpoved(hodnota: String) {
        val odpoved = hodnota.toIntOrNull()
        val jeSpravne = odpoved == cislo1 * cislo2
        
        scope.launch {
            farbaPozadia = if (jeSpravne) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
            if (jeSpravne) spravneOdpovede++
            delay(400)
            farbaPozadia = Color.Transparent
            
            if (pocetPrikladov < limitPrikladov - 1) {
                pocetPrikladov++
                cislo1 = (1..12).random()
                cislo2 = (1..12).random()
                vstupPouzivatela = ""
                startTimePriklad = System.currentTimeMillis()
            } else {
                celkovyCas = System.currentTimeMillis() - startTimeCelkovo
                zobrazVysledok = true
            }
        }
    }

    // Hlasový vstup s automatickým odoslaním
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val hovoreneCislo = data?.get(0)?.filter { it.isDigit() } ?: ""
            if (hovoreneCislo.isNotEmpty()) {
                vstupPouzivatela = hovoreneCislo
                spracujOdpoved(hovoreneCislo) // Automatické potvrdenie
            }
        }
    }

    // Časovač pre jednotlivé príklady
    LaunchedEffect(hraBezi, pocetPrikladov, zobrazVysledok) {
        if (hraBezi && !zobrazVysledok) {
            startTimePriklad = System.currentTimeMillis()
            while (hraBezi && !zobrazVysledok) {
                aktualneSekundy = ((System.currentTimeMillis() - startTimePriklad) / 1000).toInt()
                delay(500)
            }
        }
    }

    when {
        !hraBezi -> {
            // ÚVODNÁ OBRAZOVKA - NASTAVENIA
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Nastavenia tréningu", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(30.dp))
                
                Text("Počet príkladov:", fontSize = 18.sp)
                Row(Modifier.padding(8.dp)) {
                    listOf(5, 10, 15).forEach { pocet ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.selectable(
                                selected = (pocet == limitPrikladov),
                                onClick = { limitPrikladov = pocet }
                            ).padding(8.dp)
                        ) {
                            RadioButton(selected = (pocet == limitPrikladov), onClick = null)
                            Text("$pocet", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Jazyk pre hlas:", fontSize = 18.sp)
                Row(Modifier.padding(8.dp)) {
                    jazyky.forEach { jaz ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.selectable(selected = (jaz == zvolenyJazyk), onClick = { zvolenyJazyk = jaz }).padding(8.dp)
                        ) {
                            RadioButton(selected = (jaz == zvolenyJazyk), onClick = null)
                            Text(jaz, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))
                Button(
                    onClick = {
                        hraBezi = true
                        startTimeCelkovo = System.currentTimeMillis()
                        startTimePriklad = System.currentTimeMillis()
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text("ŠTART", fontSize = 20.sp)
                }
            }
        }

        zobrazVysledok -> {
            StatistikaScreen(spravneOdpovede, limitPrikladov, celkovyCas) {
                hraBezi = false
                zobrazVysledok = false
                pocetPrikladov = 0
                spravneOdpovede = 0
                vstupPouzivatela = ""
            }
        }

        else -> {
            // HRA
            Column(
                modifier = Modifier.fillMaxSize().background(animovanaFarba).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Príklad ${pocetPrikladov + 1} / $limitPrikladov", fontSize = 20.sp)
                Text(text = "Čas: $aktualneSekundy s", color = Color.Gray)
                
                Spacer(Modifier.height(40.dp))
                Text(text = "$cislo1 × $cislo2 =", fontSize = 72.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(30.dp))

                OutlinedTextField(
                    value = vstupPouzivatela,
                    onValueChange = { if (it.all { c -> c.isDigit() }) vstupPouzivatela = it },
                    label = { Text(if (zvolenyJazyk == "Slovenčina") "Odpoveď" else "Antwort") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (zvolenyJazyk == "Slovenčina") "sk-SK" else "de-DE")
                            }
                            launcher.launch(intent)
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "Hlas")
                        }
                    }
                )

                Button(
                    onClick = { spracujOdpoved(vstupPouzivatela) },
                    modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(56.dp)
                ) {
                    Text(if (zvolenyJazyk == "Slovenčina") "POTVRDIŤ" else "BESTÄTIGEN")
                }
            }
        }
    }
}

@Composable
fun StatistikaScreen(spravne: Int, celkovo: Int, cas: Long, onRestart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📊 Výsledky", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("Úspešnosť: $spravne / $celkovo", fontSize = 24.sp)
        Text("Celkový čas: ${cas / 1000} s", fontSize = 20.sp)
        Text("Priemer: ${"%.2f".format((cas.toDouble() / celkovo) / 1000.0)} s / príklad", fontSize = 16.sp)
        Spacer(Modifier.height(40.dp))
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("MENU") }
    }
}
