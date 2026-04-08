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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SkipNext
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChybnyPriklad(val c1: Int, val c2: Int)
data class ZaznamKola(val id: Long, val poradie: Int, val spravne: Int, val celkovo: Int, val casSekundy: Double)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatematickyTrener() {
    val historiaKol = remember { mutableStateListOf<ZaznamKola>() }
    val scope = rememberCoroutineScope()
    
    var zvolenyJazyk by remember { mutableStateOf("Slovenčina") }
    val t = remember(zvolenyJazyk) {
        if (zvolenyJazyk == "Slovenčina") {
            mapOf(
                "title" to "Mathe Trainer", "limitX" to "Násobilka do:", "limitP" to "Počet príkladov:",
                "lang" to "Jazyk:", "start" to "ŠTART", "history" to "Dnešná história:",
                "round" to "Kolo", "example" to "Príklad", "time" to "Čas", "answer" to "Odpoveď",
                "confirm" to "POTVRDIŤ", "results" to "📊 Výsledky", "success" to "Úspešnosť",
                "totalTime" to "Celkový čas", "average" to "Priemer na príklad", "correction" to "Oprava chýb:", 
                "perfect" to "Perfektné! 🏆", "retry" to "ZNOVA", "menu" to "MENU", 
                "wrong" to "Nesprávne! Oprav:", "skip" to "PRESKOČIŤ"
            )
        } else {
            mapOf(
                "title" to "Mathe Trainer", "limitX" to "Einmaleins bis:", "limitP" to "Anzahl der Aufgaben:",
                "lang" to "Sprache:", "start" to "START", "history" to "Heutiger Verlauf:",
                "round" to "Runde", "example" to "Aufgabe", "time" to "Zeit", "answer" to "Antwort",
                "confirm" to "BESTÄTIGEN", "results" to "📊 Ergebnisse", "success" to "Erfolg",
                "totalTime" to "Gesamtzeit", "average" to "Schnitt pro Aufgabe", "correction" to "Fehlerkorrektur:", 
                "perfect" to "Perfekt! 🏆", "retry" to "WIEDERHOLEN", "menu" to "MENÜ", 
                "wrong" to "Falsch! Korrigiere:", "skip" to "ÜBERSPRINGEN"
            )
        }
    }

    var hraBezi by remember { mutableStateOf(false) }
    var zobrazVysledok by remember { mutableStateOf(false) }
    var rezimOpravy by remember { mutableStateOf(false) }
    var limitPrikladov by remember { mutableIntStateOf(10) }
    var hornaHranicaNasobilky by remember { mutableStateOf("10") } 
    var expandedX by remember { mutableStateOf(false) }
    var expandedPocet by remember { mutableStateOf(false) }
    var cislo1 by remember { mutableIntStateOf(1) }
    var cislo2 by remember { mutableIntStateOf(1) }
    var vstupPouzivatela by remember { mutableStateOf("") }
    var pocetPrikladov by remember { mutableIntStateOf(0) }
    var spravneOdpovede by remember { mutableIntStateOf(0) }
    val chybnePriklady = remember { mutableStateListOf<ChybnyPriklad>() }
    var startTimeCelkovo by remember { mutableLongStateOf(0L) }
    var startTimePriklad by remember { mutableLongStateOf(0L) }
    var aktualneSekundy by remember { mutableIntStateOf(0) }
    var celkovyCas by remember { mutableLongStateOf(0L) }
    var farbaPozadia by remember { mutableStateOf(Color.Transparent) }
    val animovanaFarba by animateColorAsState(targetValue = farbaPozadia, label = "bg_color")

    val generujPriklad = {
        val max = hornaHranicaNasobilky.toIntOrNull() ?: 10
        cislo1 = (1..max).random()
        cislo2 = (1..max).random()
    }

    val startNovehoKola = {
        pocetPrikladov = 0
        spravneOdpovede = 0
        vstupPouzivatela = ""
        aktualneSekundy = 0
        chybnePriklady.clear()
        zobrazVysledok = false
        rezimOpravy = false
        hraBezi = true
        generujPriklad()
        startTimeCelkovo = System.currentTimeMillis()
        startTimePriklad = System.currentTimeMillis()
    }

    fun spracujOdpoved(hodnota: String) {
        val odpoved = hodnota.toIntOrNull() ?: return
        val jeSpravne = odpoved == (cislo1 * cislo2)
        scope.launch {
            if (jeSpravne) {
                farbaPozadia = Color(0xFFC8E6C9)
                if (!rezimOpravy) spravneOdpovede++
                delay(400)
                farbaPozadia = Color.Transparent
                if (pocetPrikladov < limitPrikladov - 1) {
                    pocetPrikladov++
                    generujPriklad()
                    vstupPouzivatela = ""
                    rezimOpravy = false
                    startTimePriklad = System.currentTimeMillis()
                } else {
                    celkovyCas = System.currentTimeMillis() - startTimeCelkovo
                    historiaKol.add(ZaznamKola(System.currentTimeMillis(), historiaKol.size + 1, spravneOdpovede, limitPrikladov, celkovyCas / 1000.0))
                    zobrazVysledok = true
                }
            } else {
                farbaPozadia = Color(0xFFFFCDD2)
                delay(400)
                farbaPozadia = Color.Transparent
                vstupPouzivatela = ""
                rezimOpravy = true
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

    LaunchedEffect(hraBezi, pocetPrikladov, zobrazVysledok, rezimOpravy) {
        if (hraBezi && !zobrazVysledok && !rezimOpravy) {
            val baseTime = System.currentTimeMillis() - (aktualneSekundy * 1000L)
            while (isActive && hraBezi && !zobrazVysledok && !rezimOpravy) {
                aktualneSekundy = ((System.currentTimeMillis() - baseTime) / 1000).toInt()
                delay(500)
            }
        }
    }

    when {
        !hraBezi -> {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t["title"]!!, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(32.dp))
                Text(t["limitX"]!!)
                ExposedDropdownMenuBox(expanded = expandedX, onExpandedChange = { expandedX = !expandedX }) {
                    OutlinedTextField(
                        value = hornaHranicaNasobilky, onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedX) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(0.6f)
                    )
                    ExposedDropdownMenu(expanded = expandedX, onDismissRequest = { expandedX = false }) {
                        (1..20).forEach { i -> DropdownMenuItem(text = { Text(i.toString()) }, onClick = { hornaHranicaNasobilky = i.toString(); expandedX = false }) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(t["limitP"]!!)
                ExposedDropdownMenuBox(expanded = expandedPocet, onExpandedChange = { expandedPocet = !expandedPocet }) {
                    OutlinedTextField(
                        value = limitPrikladov.toString(), onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPocet) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(0.6f)
                    )
                    ExposedDropdownMenu(expanded = expandedPocet, onDismissRequest = { expandedPocet = false }) {
                        listOf(5, 10, 15, 20, 30).forEach { p -> DropdownMenuItem(text = { Text(p.toString()) }, onClick = { limitPrikladov = p; expandedPocet = false }) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(t["lang"]!!)
                Row {
                    listOf("Slovenčina", "Deutsch").forEach { jaz ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            RadioButton(jaz == zvolenyJazyk, onClick = { zvolenyJazyk = jaz })
                            Text(jaz)
                        }
                    }
                }
                if (historiaKol.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(t["history"]!!, fontWeight = FontWeight.SemiBold)
                    LazyColumn(modifier = Modifier.height(120.dp)) {
                        items(historiaKol.reversed(), key = { it.id }) { 
                            Text("${t["round"]} ${it.poradie}: ${it.spravne}/${it.celkovo} (${"%.1f".format(it.casSekundy)}s)", fontSize = 14.sp) 
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { startNovehoKola() }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text(t["start"]!!) }
            }
        }
        zobrazVysledok -> {
            StatistikaScreen(spravneOdpovede, limitPrikladov, celkovyCas, chybnePriklady, t, { startNovehoKola() }, { hraBezi = false; zobrazVysledok = false })
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize().background(animovanaFarba).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${t["example"]} ${pocetPrikladov + 1} / $limitPrikladov")
                Text("${t["time"]}: $aktualneSekundy s", color = Color.Gray)
                Spacer(Modifier.height(48.dp))
                Text(text = if (rezimOpravy) t["wrong"]!! else "$cislo1 × $cislo2 =", fontSize = 32.sp, fontWeight = FontWeight.Black, color = if (rezimOpravy) Color.Red else Color.Unspecified)
                if (rezimOpravy) Text("$cislo1 × $cislo2 =", fontSize = 72.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                Spacer(Modifier.height(32.dp))
                OutlinedTextField(
                    value = vstupPouzivatela,
                    onValueChange = { if (it.all { c -> c.isDigit() }) {
                        vstupPouzivatela = it
                        val target = (cislo1 * cislo2).toString()
                        if (rezimOpravy && it.length == target.length) spracujOdpoved(it)
                    }},
                    label = { Text(if (rezimOpravy) t["correction"]!! else t["answer"]!!) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = { IconButton(onClick = { 
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (zvolenyJazyk == "Slovenčina") "sk-SK" else "de-DE")
                        }
                        launcher.launch(intent)
                    }) { Icon(Icons.Default.Mic, null) } }
                )
                if (rezimOpravy) {
                    Row(modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
                        OutlinedButton(onClick = { chybnePriklady.add(ChybnyPriklad(cislo1, cislo2)); scope.launch { if (pocetPrikladov < limitPrikladov - 1) { pocetPrikladov++; generujPriklad(); vstupPouzivatela = ""; rezimOpravy = false; startTimePriklad = System.currentTimeMillis() } else { celkovyCas = System.currentTimeMillis() - startTimeCelkovo; historiaKol.add(ZaznamKola(System.currentTimeMillis(), historiaKol.size + 1, spravneOdpovede, limitPrikladov, celkovyCas / 1000.0)); zobrazVysledok = true } } }, modifier = Modifier.weight(1f).height(56.dp)) { Icon(Icons.Default.SkipNext, null); Spacer(Modifier.width(8.dp)); Text(t["skip"]!!) }
                        Button(onClick = { spracujOdpoved(vstupPouzivatela) }, modifier = Modifier.weight(1f).padding(start = 8.dp).height(56.dp)) { Text(t["confirm"]!!) }
                    }
                } else {
                    Button(onClick = { spracujOdpoved(vstupPouzivatela) }, modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(56.dp)) { Text(t["confirm"]!!) }
                }
            }
        }
    }
}

@Composable
fun StatistikaScreen(spravne: Int, celkovo: Int, cas: Long, chybne: List<ChybnyPriklad>, t: Map<String, String>, onRetry: () -> Unit, onMenu: () -> Unit) {
    val celkoveSekundy = cas / 1000.0
    val priemer = if (celkovo > 0) celkoveSekundy / celkovo else 0.0
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t["results"]!!, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("${t["success"]}: $spravne / $celkovo", fontSize = 22.sp)
        Text("${t["totalTime"]}: ${"%.1f".format(celkoveSekundy)} s", fontSize = 18.sp)
        Text("${t["average"]!!}: ${"%.2f".format(priemer)} s", fontSize = 16.sp, color = Color(0xFF1976D2))
        if (chybne.isNotEmpty()) {
            Spacer(Modifier.height(16.dp)); Text(t["correction"]!!, color = Color.Red, fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.weight(1f)) { items(chybne) { ChybnyPrikladItem(it) } }
        } else { Spacer(Modifier.weight(1f)); Text(t["perfect"]!!, color = Color(0xFF388E3C), fontSize = 24.sp) }
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(t["retry"]!!) }
        OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(t["menu"]!!) }
    }
}

@Composable
fun ChybnyPrikladItem(priklad: ChybnyPriklad) {
    var opravaVstup by remember { mutableStateOf("") }
    var opravene by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(4.dp), colors = CardDefaults.cardColors(containerColor = if (opravene) Color(0xFFE8F5E9) else Color(0xFFFFF3F3))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${priklad.c1} × ${priklad.c2} =", modifier = Modifier.width(100.dp))
            if (!opravene) {
                OutlinedTextField(value = opravaVstup, onValueChange = { if (it.all { c -> c.isDigit() }) { opravaVstup = it; if (it.toIntOrNull() == (priklad.c1 * priklad.c2)) opravene = true } }, modifier = Modifier.width(80.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                IconButton(onClick = { if (opravaVstup.toIntOrNull() == (priklad.c1 * priklad.c2)) opravene = true }) { Icon(Icons.Default.CheckCircle, null, tint = if (opravaVstup.isNotEmpty()) Color.Black else Color.Gray) }
            } else {
                Text("${priklad.c1 * priklad.c2}", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                Spacer(Modifier.weight(1f)); Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF388E3C))
            }
        }
    }
}
