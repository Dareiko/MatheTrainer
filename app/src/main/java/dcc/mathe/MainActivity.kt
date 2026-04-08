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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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

data class ChybnyPriklad(val c1: Int, val c2: Int)

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
    
    val chybnePriklady = remember { mutableStateListOf<ChybnyPriklad>() }
    
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

    // Funkcia na ÚPLNÝ REŠTART všetkých hodnôt
    fun resetujHru() {
        pocetPrikladov = 0
        spravneOdpovede = 0
        vstupPouzivatela = ""
        aktualneSekundy = 0
        chybnePriklady.clear()
        cislo1 = (1..12).random()
        cislo2 = (1..12).random()
        zobrazVysledok = false
        hraBezi = false
    }

    fun spracujOdpoved(hodnota: String) {
        val odpoved = hodnota.toIntOrNull()
        val jeSpravne = odpoved == cislo1 * cislo2
        
        scope.launch {
            farbaPozadia = if (jeSpravne) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
            if (jeSpravne) {
                spravneOdpovede++
            } else {
                chybnePriklady.add(ChybnyPriklad(cislo1, cislo2))
            }
            delay(400)
            farbaPozadia = Color.Transparent
            
            if (pocetPrikladov < limitPrikladov - 1) {
                pocetPrikladov++
                cislo1 = (1..12).random()
                cislo2 = (1..12).random()
                vstupPouzivatela = "" // Vynulovanie vstupu pre nový príklad
                startTimePriklad = System.currentTimeMillis()
            } else {
                celkovyCas = System.currentTimeMillis() - startTimeCelkovo
                zobrazVysledok = true
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val hovoreneCislo = data?.get(0)?.filter { it.isDigit() } ?: ""
            if (hovoreneCislo.isNotEmpty()) {
                vstupPouzivatela = hovoreneCislo
                spracujOdpoved(hovoreneCislo)
            }
        }
    }

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
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Nastavenia tréningu", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(30.dp))
                Text("Počet príkladov:")
                Row {
                    listOf(5, 10, 15).forEach { pocet ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.selectable(selected = (pocet == limitPrikladov), onClick = { limitPrikladov = pocet }).padding(8.dp)) {
                            RadioButton(selected = (pocet == limitPrikladov), onClick = null)
                            Text("$pocet")
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
                Button(onClick = { 
                    resetujHru() // Vyčistíme všetko pred štartom
                    hraBezi = true
                    startTimeCelkovo = System.currentTimeMillis() 
                }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("ŠTART") }
            }
        }

        zobrazVysledok -> {
            StatistikaScreen(spravneOdpovede, limitPrikladov, celkovyCas, chybnePriklady) {
                resetujHru() // Návrat do menu vyčistí hru
            }
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize().background(animovanaFarba).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Príklad ${pocetPrikladov + 1} / $limitPrikladov")
                Text("Čas: $aktualneSekundy s", color = Color.Gray)
                Spacer(Modifier.height(40.dp))
                Text("$cislo1 × $cislo2 =", fontSize = 72.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(30.dp))
                OutlinedTextField(
                    value = vstupPouzivatela,
                    onValueChange = { if (it.all { c -> c.isDigit() }) vstupPouzivatela = it },
                    label = { Text("Odpoveď") },
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
                            Icon(Icons.Default.Mic, contentDescription = null) 
                        } 
                    }
                )
                Button(onClick = { spracujOdpoved(vstupPouzivatela) }, modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(56.dp)) { Text("POTVRDIŤ") }
            }
        }
    }
}

@Composable
fun StatistikaScreen(spravne: Int, celkovo: Int, cas: Long, chybne: List<ChybnyPriklad>, onRestart: () -> Unit) {
    // Výpočet priemeru
    val celkoveSekundy = cas / 1000.0
    val priemer = if (celkovo > 0) celkoveSekundy / celkovo else 0.0

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📊 Výsledky", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Úspešnosť: $spravne / $celkovo", fontSize = 22.sp)
        Text("Celkový čas: ${celkoveSekundy.toInt()} s", fontSize = 18.sp)
        // Zobrazenie priemeru na 2 desatinné miesta
        Text("Priemer na príklad: ${"%.2f".format(priemer)} s", fontSize = 18.sp, color = Color(0xFF1976D2))

        if (chybne.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Oprava chýb:", fontWeight = FontWeight.Bold, color = Color.Red)
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(chybne) { priklad ->
                    ChybnyPrikladItem(priklad)
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
            Text("Bezchybné kolo! 🏆", fontSize = 24.sp, color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
        }

        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("MENU / REŠTART") }
    }
}

@Composable
fun ChybnyPrikladItem(priklad: ChybnyPriklad) {
    var opravaVstup by remember { mutableStateOf("") }
    var opravene by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
        colors = CardDefaults.cardColors(containerColor = if (opravene) Color(0xFFE8F5E9) else Color(0xFFFFF3F3))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${priklad.c1} × ${priklad.c2} =", modifier = Modifier.width(100.dp), fontSize = 18.sp)
            
            if (!opravene) {
                OutlinedTextField(
                    value = opravaVstup,
                    onValueChange = { if (it.all { c -> c.isDigit() }) opravaVstup = it },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                IconButton(onClick = {
                    if (opravaVstup.toIntOrNull() == priklad.c1 * priklad.c2) {
                        opravene = true
                    }
                }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "OK", tint = Color.Gray)
                }
            } else {
                Text("${priklad.c1 * priklad.c2}", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C), fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF388E3C))
            }
        }
    }
}
