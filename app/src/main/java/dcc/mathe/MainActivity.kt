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
data class ZaznamKola(val poradie: Int, val spravne: Int, val celkovo: Int, val casSekundy: Double)

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
    // Globálna história (prežije reštart kola, ale nie vypnutie app)
    val historiaKol = remember { mutableStateListOf<ZaznamKola>() }
    
    // Nastavenia
    var hraBezi by remember { mutableStateOf(false) }
    var limitPrikladov by remember { mutableIntStateOf(5) }
    var hornaHranicaNasobilky by remember { mutableStateOf("10") }
    
    // Aktuálne kolo
    var cislo1 by remember { mutableIntStateOf(1) }
    var cislo2 by remember { mutableIntStateOf(1) }
    var vstupPouzivatela by remember { mutableStateOf("") }
    var pocetPrikladov by remember { mutableIntStateOf(0) }
    var spravneOdpovede by remember { mutableIntStateOf(0) }
    val chybnePriklady = remember { mutableStateListOf<ChybnyPriklad>() }
    
    // Čas
    var startTimeCelkovo by remember { mutableLongStateOf(0L) }
    var startTimePriklad by remember { mutableLongStateOf(0L) }
    var aktualneSekundy by remember { mutableIntStateOf(0) }
    var celkovyCas by remember { mutableLongStateOf(0L) }
    
    var zobrazVysledok by remember { mutableStateOf(false) }
    var farbaPozadia by remember { mutableStateOf(Color.Transparent) }
    val animovanaFarba by animateColorAsState(targetValue = farbaPozadia, label = "")
    val scope = rememberCoroutineScope()
    var zvolenyJazyk by remember { mutableStateOf("Slovenčina") }

    fun generujPriklad() {
        val max = hornaHranicaNasobilky.toIntOrNull()?.coerceIn(1, 20) ?: 10
        cislo1 = (1..max).random()
        cislo2 = (1..max).random()
    }

    fun resetujHru() {
        pocetPrikladov = 0
        spravneOdpovede = 0
        vstupPouzivatela = ""
        aktualneSekundy = 0
        chybnePriklady.clear()
        zobrazVysledok = false
        hraBezi = false
    }

    fun spracujOdpoved(hodnota: String) {
        val odpoved = hodnota.toIntOrNull()
        val jeSpravne = odpoved == cislo1 * cislo2
        
        scope.launch {
            farbaPozadia = if (jeSpravne) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
            if (jeSpravne) spravneOdpovede++ else chybnePriklady.add(ChybnyPriklad(cislo1, cislo2))
            
            delay(400)
            farbaPozadia = Color.Transparent
            
            if (pocetPrikladov < limitPrikladov - 1) {
                pocetPrikladov++
                generujPriklad()
                vstupPouzivatela = ""
                startTimePriklad = System.currentTimeMillis()
            } else {
                celkovyCas = System.currentTimeMillis() - startTimeCelkovo
                historiaKol.add(ZaznamKola(historiaKol.size + 1, spravneOdpovede, limitPrikladov, celkovyCas / 1000.0))
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
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tréner násobilky", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                
                // Nastavenie rozsahu
                Text("Násobilka od 1 do (max 20):")
                OutlinedTextField(
                    value = hornaHranicaNasobilky,
                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) hornaHranicaNasobilky = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp)
                )

                Spacer(Modifier.height(16.dp))
                Text("Počet príkladov:")
                Row {
                    listOf(5, 10, 15).forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.selectable(p == limitPrikladov, onClick = { limitPrikladov = p }).padding(8.dp)) {
                            RadioButton(p == limitPrikladov, null)
                            Text("$p")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                if (historiaKol.isNotEmpty()) {
                    Text("História dnešných kôl:", fontWeight = FontWeight.SemiBold)
                    LazyColumn(modifier = Modifier.height(150.dp)) {
                        items(historiaKol.reversed()) { kolo ->
                            Text("Kolo ${kolo.poradie}: ${kolo.spravne}/${kolo.celkovo} za ${"%.1f".format(kolo.casSekundy)}s", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Button(onClick = { 
                    resetujHru()
                    generujPriklad()
                    hraBezi = true
                    startTimeCelkovo = System.currentTimeMillis() 
                }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("ŠTART") }
            }
        }

        zobrazVysledok -> {
            StatistikaScreen(spravneOdpovede, limitPrikladov, celkovyCas, chybnePriklady) { resetujHru() }
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
                    trailingIcon = { IconButton(onClick = { /* Hlasový intent */ }) { Icon(Icons.Default.Mic, null) } }
                )
                Button(onClick = { spracujOdpoved(vstupPouzivatela) }, modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(56.dp)) { Text("POTVRDIŤ") }
            }
        }
    }
}

@Composable
fun StatistikaScreen(spravne: Int, celkovo: Int, cas: Long, chybne: List<ChybnyPriklad>, onRestart: () -> Unit) {
    val priemer = if (celkovo > 0) (cas / 1000.0) / celkovo else 0.0
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📊 Výsledky kola", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Úspešnosť: $spravne / $celkovo", fontSize = 22.sp)
        Text("Priemer: ${"%.2f".format(priemer)} s / príklad", color = Color(0xFF1976D2))

        if (chybne.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Oprava chýb:", color = Color.Red, fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chybne) { ChybnyPrikladItem(it) }
            }
        } else {
            Spacer(Modifier.weight(1f))
            Text("Perfektné! 🏆", fontSize = 30.sp, color = Color(0xFF388E3C))
        }
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("SPÄŤ DO MENU") }
    }
}

@Composable
fun ChybnyPrikladItem(priklad: ChybnyPriklad) {
    var opravaVstup by remember { mutableStateOf("") }
    var opravene by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(4.dp), colors = CardDefaults.cardColors(containerColor = if (opravene) Color(0xFFE8F5E9) else Color(0xFFFFF3F3))) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${priklad.c1} × ${priklad.c2} =", modifier = Modifier.width(80.dp))
            if (!opravene) {
                OutlinedTextField(value = opravaVstup, onValueChange = { if (it.all { c -> c.isDigit() }) opravaVstup = it }, modifier = Modifier.width(80.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                IconButton(onClick = { if (opravaVstup.toIntOrNull() == priklad.c1 * priklad.c2) opravene = true }) { Icon(Icons.Default.CheckCircle, null, tint = Color.Gray) }
            } else {
                Text("${priklad.c1 * priklad.c2}", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF388E3C))
            }
        }
    }
}
