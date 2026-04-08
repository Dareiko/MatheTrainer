package dcc.mathe

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha // PRIDANÝ IMPORT
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// --- MODELY ---
data class ChybnyPriklad(val c1: Int, val c2: Int)
data class ZaznamKola(val id: Long, val poradie: Int, val typX: String, val spravne: Int, val celkovo: Int, val casSekundy: Double)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContent { MatematickyTrenerApp() }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

fun safeVibrate(context: Context, isOk: Boolean) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.let {
            if (it.hasVibrator()) {
                if (isOk) it.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                else it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 100, 50), -1))
            }
        }
    } catch (e: Exception) {}
}

@Composable
fun TrendovyGraf(historia: List<ZaznamKola>, isDark: Boolean) {
    if (historia.size < 2) return
    val data = historia.takeLast(10).map { it.casSekundy / it.celkovo }
    val maxT = (data.maxOrNull() ?: 1.0).coerceAtLeast(0.1)
    val minT = data.minOrNull() ?: 0.0
    val range = (maxT - minT).coerceAtLeast(0.1)
    val color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp).padding(vertical = 4.dp)) {
        val w = size.width
        val h = size.height
        val spacing = if (data.size > 1) w / (data.size - 1) else w
        val points = data.mapIndexed { i, time ->
            Offset(i * spacing, h - ((time - minT) / range * h).toFloat())
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(3.dp.toPx()))
    }
}

@Composable
fun MatematickyTrenerApp() {
    var themeMode by remember { mutableStateOf("system") }
    val useDark = when(themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (useDark) darkColorScheme(primary = Color(0xFF81C784)) else lightColorScheme()) {
        MatematickyTrener(themeMode) { themeMode = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatematickyTrener(themeMode: String, onThemeToggle: (String) -> Unit) {
    val historiaKol = remember { mutableStateListOf<ZaznamKola>() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val context = LocalContext.current
    var jazyk by remember { mutableStateOf("Slovenčina") }
    
    val t = remember(jazyk) {
        if (jazyk == "Slovenčina") mapOf(
            "title" to "Mathe Trainer", "start" to "ŠTART", "history" to "Štatistiky & Trend:",
            "example" to "Príklad", "results" to "📊 Výsledky", "success" to "Úspešnosť",
            "menu" to "MENU", "exit" to "Koniec", "limitX" to "Násobilka do:", "limitP" to "Počet príkladov:"
        ) else mapOf(
            "title" to "Mathe Trainer", "start" to "START", "history" to "Statistik & Trend:",
            "example" to "Aufgabe", "results" to "📊 Ergebnisse", "success" to "Erfolg",
            "menu" to "MENÜ", "exit" to "Ende", "limitX" to "Einmaleins bis:", "limitP" to "Anzahl:"
        )
    }

    var hraBezi by remember { mutableStateOf(false) }
    var zobrazVysledok by remember { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(10) }
    var typX by remember { mutableStateOf("10") }
    var n1 by remember { mutableIntStateOf(1) }
    var n2 by remember { mutableIntStateOf(1) }
    var vstup by remember { mutableStateOf("") }
    var aktualnyIndex by remember { mutableIntStateOf(0) }
    var body by remember { mutableIntStateOf(0) }
    var startT by remember { mutableLongStateOf(0L) }
    var sekundy by remember { mutableIntStateOf(0) }
    var bgFarba by remember { mutableStateOf(Color.Transparent) }
    val animBg by animateColorAsState(bgFarba, label = "bg")
    val progressValue = if(hraBezi && limit > 0) (aktualnyIndex.toFloat() / limit) else 0f

    val generuj = {
        val max = typX.toIntOrNull() ?: 10
        fun vaha(i: Int) = when(i){ 1->1; 2,5,10->2; in 6..9->4; else->3 }
        val pool = (1..max).flatMap { i -> List(vaha(i)){i} }
        var a = pool.random(); var b = pool.random()
        if(a<3 && b<3) { if((0..1).random()==0) a=(3..max).random() else b=(3..max).random() }
        n1 = a; n2 = b
    }

    fun potvrd(valStr: String) {
        val res = valStr.toIntOrNull() ?: return
        scope.launch {
            if (res == n1 * n2) {
                safeVibrate(context, true)
                bgFarba = Color(0xFFC8E6C9).copy(alpha = 0.5f)
                body++
                delay(300); bgFarba = Color.Transparent
                if (aktualnyIndex < limit - 1) {
                    aktualnyIndex++; generuj(); vstup = ""
                } else {
                    historiaKol.add(ZaznamKola(System.currentTimeMillis(), historiaKol.size+1, typX, body, limit, (System.currentTimeMillis() - startT)/1000.0))
                    zobrazVysledok = true
                }
            } else {
                safeVibrate(context, false)
                bgFarba = Color(0xFFFFCDD2).copy(alpha = 0.5f)
                delay(300); bgFarba = Color.Transparent; vstup = ""
            }
        }
    }

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == ComponentActivity.RESULT_OK) {
            val d = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val num = d?.get(0)?.filter { it.isDigit() } ?: ""
            if (num.isNotEmpty()) { vstup = num; potvrd(num) }
        }
    }

    LaunchedEffect(hraBezi, aktualnyIndex, zobrazVysledok) {
        if (hraBezi && !zobrazVysledok) {
            val start = System.currentTimeMillis() - (sekundy * 1000L)
            while (isActive && hraBezi && !zobrazVysledok) {
                sekundy = ((System.currentTimeMillis() - start) / 1000).toInt()
                delay(500)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Mathe Trainer", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                Divider()
                NavigationDrawerItem(label={Text("Jazyk: $jazyk")}, selected=false, onClick={jazyk = if(jazyk=="Slovenčina") "Deutsch" else "Slovenčina"})
                NavigationDrawerItem(label={Text("Téma: $themeMode")}, selected=false, onClick={onThemeToggle(if(themeMode=="light") "dark" else "light")})
                NavigationDrawerItem(label={Text(t["exit"]!!)}, selected=false, onClick={(context as? ComponentActivity)?.finish()})
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title={Text(t["title"]!!)}, 
                        navigationIcon={IconButton(onClick={scope.launch{drawerState.open()}}){Icon(Icons.Default.Menu, null)}}
                    )
                    if(hraBezi && !zobrazVysledok) LinearProgressIndicator(progress = progressValue, modifier = Modifier.fillMaxWidth())
                }
            },
            bottomBar = {
                Text(
                    text = "DCC, všetky práva vyhradené, 2026",
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray.copy(alpha = 0.7f) // Zmenené pre kompatibilitu
                )
            }
        ) { p ->
            Box(Modifier.fillMaxSize().padding(p)) {
                when {
                    !hraBezi -> Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        var exX by remember { mutableStateOf(false) }
                        var exP by remember { mutableStateOf(false) }

                        Text(t["limitX"]!!)
                        ExposedDropdownMenuBox(exX, { exX = !exX }) {
                            OutlinedTextField(value = typX, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exX) }, modifier = Modifier.menuAnchor().width(150.dp))
                            ExposedDropdownMenu(exX, { exX = false }) {
                                (10..20).forEach { i -> DropdownMenuItem(text = { Text(i.toString()) }, onClick = { typX = i.toString(); exX = false }) }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(t["limitP"]!!)
                        ExposedDropdownMenuBox(exP, { exP = !exP }) {
                            OutlinedTextField(value = limit.toString(), onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exP) }, modifier = Modifier.menuAnchor().width(150.dp))
                            ExposedDropdownMenu(exP, { exP = false }) {
                                listOf(5, 10, 15, 20, 30).forEach { i -> DropdownMenuItem(text = { Text(i.toString()) }, onClick = { limit = i; exP = false }) }
                            }
                        }

                        if (historiaKol.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(t["history"]!!, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            TrendovyGraf(historiaKol, isSystemInDarkTheme() || themeMode == "dark")
                            
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
                                listOf("Typ" to 1f, "N" to 0.7f, "Čas" to 1.3f, "%" to 0.8f, "OK" to 0.7f).forEach { (txt, w) ->
                                    Text(txt, Modifier.weight(w), fontSize = 10.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                }
                            }
                            LazyColumn(Modifier.weight(1f).fillMaxWidth().border(0.5.dp, Color.Gray)) {
                                items(historiaKol.reversed(), key = { it.id }) { k ->
                                    val u = (k.spravne.toDouble() / k.celkovo * 100).toInt()
                                    Row(Modifier.fillMaxWidth().padding(4.dp).border(0.5.dp, Color.LightGray)) {
                                        Text("1-${k.typX}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text("${k.celkovo}", Modifier.weight(0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text("${"%.1f".format(k.casSekundy)}s", Modifier.weight(1.3f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text("$u%", Modifier.weight(0.8f), fontSize = 11.sp, textAlign = TextAlign.Center, color = if (u < 50) Color.Red else MaterialTheme.colorScheme.primary)
                                        Text("${k.spravne}", Modifier.weight(0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        } else { Spacer(Modifier.weight(1f)) }

                        Button({ startT = System.currentTimeMillis(); sekundy = 0; body = 0; aktualnyIndex = 0; hraBezi = true; generuj() }, Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) { Text(t["start"]!!) }
                    }
                    zobrazVysledok -> Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(t["results"]!!, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("${t["success"]!!}: $body / $limit", fontSize = 20.sp)
                        Button({ hraBezi = false; zobrazVysledok = false }, Modifier.padding(top = 20.dp)) { Text(t["menu"]!!) }
                    }
                    else -> Column(
                        modifier = Modifier.fillMaxSize().background(animBg).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text("${t["example"]} ${aktualnyIndex + 1} / $limit | ${sekundy}s")
                        Spacer(Modifier.height(40.dp))
                        Text("$n1 × $n2 =", fontSize = 70.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(vstup, { if (it.all { c -> c.isDigit() }) vstup = it }, Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Button({ potvrd(vstup) }, Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("POTVRDIŤ") }
                        IconButton({ val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }; mic.launch(i) }) { Icon(Icons.Default.Mic, null) }
                    }
                }
            }
        }
    }
}
