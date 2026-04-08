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

// Dátové modely
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
    val historiaKol = remember { mutableStateListOf<ZaznamKola>() }
    
    // Nastavenia
    var hraBezi by remember { mutableStateOf(false) }
    var limitPrikladov by remember { mutableIntStateOf(5) }
    var hornaHranicaNasobilky by remember { mutableStateOf("") } 
    var zvolenyJazyk by remember { mutableStateOf("Slovenčina") }
    
    // Stav kola
    var cislo1 by remember { mutableIntStateOf(1) }
    var cislo2 by remember { mutableIntStateOf(1) }
    var vstupPouzivatela by remember { mutableStateOf("") }
    var pocetPrikladov by remember { mutableIntStateOf(0) }
    var spravneOdpovede by remember { mutableIntStateOf(0) }
    val chybnePriklady = remember { mutableStateListOf<ChybnyPriklad>() }
    
    // Časomiera
    var startTimeCelkovo by remember { mutableLongStateOf(0L) }
    var startTimePriklad by remember { mutableLongStateOf(0L) }
    var aktualneSekundy by remember { mutableIntStateOf(0) }
    var celkovyCas by remember { mutableLongStateOf(0L) }
    
    var zobrazVysledok by remember { mutableStateOf(false) }
    var farbaPozadia by remember { mutableStateOf(Color.Transparent) }
    val animovanaFarba by animateColorAsState(targetValue = farbaPozadia, label = "")
    val scope = rememberCoroutineScope()

    // --- POMOCNÉ FUNKCIE ---

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

    fun startNovehoKola() {
        pocetPrikladov = 0
        spravneOdpovede = 0
        vstupPouzivatela = ""
        aktualneSekundy = 0
        chybnePriklady.clear()
        zobrazVysledok = false
        hraBezi = true
        generujPriklad()
        startTimeCelkovo = System.currentTimeMillis()
        startTimePriklad = System.currentTimeMillis()
    }

    fun resetDoMenu() {
        resetujHru()
        hornaHranicaNasobilky = "" 
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

    // --- HLASOVÉ OVLÁDANIE ---
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

    // --- POUŽÍVATEĽSKÉ ROZHRANIE ---
    when {
        !hraBezi -> {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tréner násobilky", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                
                Text("Násobilka od 1 do (1-20):", color = if (hornaHranicaNasobilky.isEmpty()) Color.Red else Color.Unspecified)
                OutlinedTextField(
                    value = hornaHranicaNasobilky,
                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) hornaHranicaNasobilky = it },
                    placeholder = { Text("Zadaj X") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                    isError = hornaHranicaNasobilky.isEmpty()
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
                Text("Jazyk pre hlas:")
                Row {
                    listOf("Slovenčina", "Deutsch").forEach { jaz ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.selectable(jaz == zvolenyJazyk, onClick = { zvolenyJazyk = jaz }).padding(8.dp)) {
                            RadioButton(jaz == zvolenyJazyk, null)
                            Text(jaz)
                        }
                    }
                }

                if (historiaKol.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("História kôl:", fontWeight = FontWeight.SemiBold)
                    LazyColumn(modifier = Modifier.height(120.dp)) {
                        items(historiaKol.reversed()) { kolo ->
                            Text("Kolo ${kolo.poradie}: ${kolo.spravne}/${kolo.celkovo} (${"%.1f".format(kolo.casSekundy)}s)", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { startNovehoKola() }, 
                    enabled = hornaHranicaNasobilky.isNotEmpty() && (hornaHranicaNasobilky.toIntOrNull() ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) { Text("ŠTART") }
            }
        }

        zobrazVysledok -> {
            StatistikaScreen(
                spravne = spravneOdpovede, 
                celkovo = limitPrikladov, 
                cas = celkovyCas, 
                chybne = chybnePriklady,
                onRetry = { startNovehoKola() },
                onMenu = { resetDoMenu() }
            )
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
                        }) { Icon(Icons.Default.Mic, null) } 
                    }
                )
                Button(onClick = { spracujOdpoved(vstupPouzivatela) }, modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(56.dp)) { Text(if (zvolenyJazyk == "Slovenčina") "POTVRDIŤ" else "BESTÄTIGEN") }
            }
        }
    }
}

@Composable
fun StatistikaScreen(spravne: Int, celkovo: Int, cas: Long, chybne: List<ChybnyPriklad>, onRetry: () -> Unit, onMenu: () -> Unit) {
    val celkoveSekundy = cas / 1000.0
    val priemer = if (celkovo > 0) celkoveSekundy / celkovo else 0.0

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📊 Výsledky", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Úspešnosť: $spravne / $celkovo", fontSize = 22.sp)
        Text("Celkový čas: ${"%.1f".format(celkoveSekundy)} s", fontSize = 18.sp)
        Text("Priemer: ${"%.2f".format(priemer)} s / príklad", color = Color(0xFF1976D2))

        if (chybne.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Oprava chýb:", fontWeight = FontWeight.Bold, color = Color.Red)
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(chybne) { priklad -> ChybnyPrikladItem(priklad) }
            }
        } else {
            Spacer(Modifier.weight(1f))
            Text("Vynikajúco! 🏆", fontSize = 24.sp, color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("HRAŤ ZNOVU") }
        OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("MENU") }
    }
}

@Composable
fun ChybnyPrikladItem(priklad: ChybnyPriklad) {
    var opravaVstup by remember { mutableStateOf("") }
    var opravene by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = if (opravene) Color(0xFFE8F5E9) else Color(0xFFFFF3F3))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${priklad.c1} × ${priklad.c2} =", modifier = Modifier.width(100.dp), fontSize = 18.sp)
            if (!opravene) {
                OutlinedTextField(value = opravaVstup, onValueChange = { if (it.all { c -> c.isDigit() }) opravaVstup = it }, modifier = Modifier.width(80.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                IconButton(onClick = { if (opravaVstup.toIntOrNull() == priklad.c1 * priklad.c2) opravene = true }) { Icon(Icons.Default.CheckCircle, null, tint = Color.Gray) }
            } else {
                Text("${priklad.c1 * priklad.c2}", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C), fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF388E3C))
            }
        }
    }
}
