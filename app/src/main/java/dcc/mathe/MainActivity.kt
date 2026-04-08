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
        // Obalenie celého štartu do try-catch, aby sme videli aspoň niečo, ak to spadne
        try {
            setContent {
                MatematickyTrenerApp()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// --- TOTÁLNE BEZPEČNÁ VIBRÁCIA ---
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
                if (isOk) {
                    it.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 100, 50), -1))
                }
            }
        }
    } catch (e: Exception) {
        // Ticho ignorujeme, hlavne nech nepadne apka
    }
}

@Composable
fun TrendovyGraf(historia: List<ZaznamKola>, isDark: Boolean) {
    if (historia.size < 2) return
    val data = historia.takeLast(10).map { it.casSekundy / it.celkovo }
    val maxT = (data.maxOrNull() ?: 1.0).coerceAtLeast(0.1)
    val minT = data.minOrNull() ?: 0.0
    val range = (maxT - minT).coerceAtLeast(0.1)
    val color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp).padding(vertical = 8.dp)) {
        val w = size.width
        val h = size.height
        val spacing = w / (data.size - 1)
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
            "title" to "Mathe Trainer", "start" to "ŠTART", "history" to "Trend & História:",
            "example" to "Príklad", "time" to "Čas", "answer" to "Odpoveď", "confirm" to "POTVRDIŤ",
            "results" to "📊 Výsledky", "success" to "Úspešnosť", "totalTime" to "Celkový čas",
            "average" to "Priemer", "correction" to "Oprava:", "perfect" to "Perfektné! 🏆",
            "retry" to "ZNOVA", "menu" to "MENU", "wrong" to "Skús znova:", "skip" to "PRESKOČIŤ",
            "theme" to "Téma", "lang" to "Jazyk", "pause" to "Pauza", "resume" to "Vpred", "exit" to "Koniec"
        ) else mapOf(
            "title" to "Mathe Trainer", "start" to "START", "history" to "Trend & Verlauf:",
            "example" to "Aufgabe", "time" to "Zeit", "answer" to "Antwort", "confirm" to "BESTÄTIGEN",
            "results" to "📊 Ergebnisse", "success" to "Erfolg", "totalTime" to "Gesamtzeit",
            "average" to "Schnitt", "correction" to "Korrektur:", "perfect" to "Perfekt! 🏆",
            "retry" to "WIEDERHOLEN", "menu" to "MENÜ", "wrong" to "Falsch:", "skip" to "ÜBERSPRINGEN",
            "theme" to "Modus", "lang" to "Sprache", "pause" to "Pause", "resume" to "Weiter", "exit" to "Ende"
        )
    }

    var hraBezi by remember { mutableStateOf(false) }
    var jePauza by remember { mutableStateOf(false) }
    var zobrazVysledok by remember { mutableStateOf(false) }
    var rezimOpravy by remember { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(10) }
    var typX by remember { mutableStateOf("10") }
    var n1 by remember { mutableIntStateOf(1) }
    var n2 by remember { mutableIntStateOf(1) }
    var vstup by remember { mutableStateOf("") }
    var aktualnyIndex by remember { mutableIntStateOf(0) }
    var body by remember { mutableIntStateOf(0) }
    val chyby = remember { mutableStateListOf<ChybnyPriklad>() }
    var startT by remember { mutableLongStateOf(0L) }
    var sekundy by remember { mutableIntStateOf(0) }
    var celkovyCas by remember { mutableLongStateOf(0L) }
    var bgFarba by remember { mutableStateOf(Color.Transparent) }
    val animBg by animateColorAsState(bgFarba, label = "bg")

    // Oprava volania LinearProgressIndicator
    val progressValue = if(hraBezi && limit > 0) (aktualnyIndex.toFloat() / limit) else 0f

    val generuj = {
        val max = typX.toIntOrNull() ?: 10
        n1 = (1..max).random()
        n2 = (1..max).random()
    }

    fun potvrd(valStr: String) {
        val res = valStr.toIntOrNull() ?: return
        scope.launch {
            if (res == n1 * n2) {
                safeVibrate(context, true)
                bgFarba = Color(0xFFC8E6C9).copy(alpha = 0.5f)
                if(!rezimOpravy) body++
                delay(300); bgFarba = Color.Transparent
                if (aktualnyIndex < limit - 1) {
                    aktualnyIndex++; generuj(); vstup = ""; rezimOpravy = false
                } else {
                    celkovyCas = System.currentTimeMillis() - startT
                    historiaKol.add(ZaznamKola(System.currentTimeMillis(), historiaKol.size+1, typX, body, limit, celkovyCas/1000.0))
                    zobrazVysledok = true
                }
            } else {
                safeVibrate(context, false)
                bgFarba = Color(0xFFFFCDD2).copy(alpha = 0.5f)
                delay(300); bgFarba = Color.Transparent; vstup = ""; rezimOpravy = true
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

    LaunchedEffect(hraBezi, aktualnyIndex, zobrazVysledok, rezimOpravy, jePauza) {
        if (hraBezi && !zobrazVysledok && !rezimOpravy && !jePauza) {
            val start = System.currentTimeMillis() - (sekundy * 1000L)
            while (isActive && hraBezi && !zobrazVysledok && !rezimOpravy && !jePauza) {
                sekundy = ((System.currentTimeMillis() - start) / 1000).toInt()
                delay(500)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Menu", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                Divider()
                NavigationDrawerItem(label={Text(t["lang"]!!)}, selected=false, onClick={jazyk = if(jazyk=="Slovenčina") "Deutsch" else "Slovenčina"})
                NavigationDrawerItem(label={Text(t["theme"]!!)}, selected=false, onClick={onThemeToggle(if(themeMode=="light") "dark" else "light")})
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
            }
        ) { p ->
            Box(Modifier.fillMaxSize().padding(p)) {
                when {
                    !hraBezi -> Column(Modifier.fillMaxSize().padding(16.dp), Alignment.CenterHorizontally) {
                        Text(t["limitX"]!!); Text(typX, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { typX = if(typX=="10") "12" else if(typX=="12") "20" else "10" })
                        Spacer(Modifier.height(20.dp))
                        TrendovyGraf(historiaKol, isSystemInDarkTheme())
                        Spacer(Modifier.weight(1f))
                        Button({ startT=System.currentTimeMillis(); sekundy=0; body=0; aktualnyIndex=0; hraBezi=true; generuj() }, Modifier.fillMaxWidth().height(60.dp)){Text(t["start"]!!)}
                    }
                    zobrazVysledok -> StatistikaScreen(body, limit, celkovyCas, t) { hraBezi=false; zobrazVysledok=false }
                    else -> Column(Modifier.fillMaxSize().background(animBg).padding(20.dp), Alignment.CenterHorizontally) {
                        Text("${t["example"]} ${aktualnyIndex+1}/$limit | ${sekundy}s")
                        Spacer(Modifier.height(40.dp))
                        Text("$n1 × $n2 =", fontSize=70.sp, fontWeight=FontWeight.Bold)
                        OutlinedTextField(vstup, { if(it.all{c->c.isDigit()}) vstup=it }, Modifier.fillMaxWidth(), keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                        Button({potvrd(vstup)}, Modifier.fillMaxWidth().padding(top=20.dp)){Text(t["confirm"]!!)}
                        IconButton({ val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }; mic.launch(i) }){Icon(Icons.Default.Mic, null)}
                    }
                }
            }
        }
    }
}

@Composable
fun StatistikaScreen(ok: Int, tot: Int, ms: Long, t: Map<String, String>, onMenu: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), Alignment.CenterHorizontally, Arrangement.Center) {
        Text(t["results"]!!, fontSize=30.sp, fontWeight=FontWeight.Bold)
        Text("${t["success"]}: $ok/$tot", fontSize=20.sp)
        Button(onMenu, Modifier.padding(top=20.dp)){Text(t["menu"]!!)}
    }
}
