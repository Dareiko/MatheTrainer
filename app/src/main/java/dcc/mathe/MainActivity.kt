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

// --- DÁTOVÉ MODELY ---
data class ChybnyPriklad(val c1: Int, val c2: Int)
data class ZaznamKola(
    val id: Long, 
    val poradie: Int, 
    val typX: String, 
    val spravne: Int, 
    val celkovo: Int, 
    val casSekundy: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MatematickyTrenerApp()
        }
    }
}

// --- POMOCNÉ FUNKCIE (Vibrácie) ---
fun vibruj(context: Context, typ: String) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (typ == "ok") {
        vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 100, 60), -1))
    }
}

// --- KOMPONENT GRAFU ---
@Composable
fun TrendovyGraf(historia: List<ZaznamKola>, isDark: Boolean) {
    if (historia.size < 2) return
    val data = historia.takeLast(10).map { it.casSekundy / it.celkovo }
    val maxTime = (data.maxOrNull() ?: 1.0).coerceAtLeast(0.1)
    val minTime = data.minOrNull() ?: 0.0
    val range = (maxTime - minTime).coerceAtLeast(0.1)
    val color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            val w = size.width
            val h = size.height
            val spacing = w / (data.size - 1)
            val points = data.mapIndexed { i, time ->
                Offset(i * spacing, h - ((time - minTime) / range * h).toFloat())
            }
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color, style = Stroke(3.dp.toPx()))
            points.forEach { drawCircle(color, 4.dp.toPx(), it) }
        }
    }
}

// --- HLAVNÝ WRAPPER (Témy) ---
@Composable
fun MatematickyTrenerApp() {
    var themeMode by remember { mutableStateOf("system") }
    val useDark = when(themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    MaterialTheme(colorScheme = if (useDark) darkColorScheme(
        primary = Color(0xFF81C784),
        surfaceVariant = Color(0xFF2C2C2C),
        background = Color(0xFF121212),
        error = Color(0xFFCF6679)
    ) else lightColorScheme()) {
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
    val isDark = isSystemInDarkTheme() || themeMode == "dark"
    
    // Lokalizácia
    var jazyk by remember { mutableStateOf("Slovenčina") }
    val t = remember(jazyk) {
        if (jazyk == "Slovenčina") mapOf(
            "title" to "Mathe Trainer", "start" to "ŠTART", "history" to "Trend & História:",
            "example" to "Príklad", "time" to "Čas", "answer" to "Odpoveď", "confirm" to "POTVRDIŤ",
            "results" to "📊 Výsledky", "success" to "Úspešnosť", "totalTime" to "Celkový čas",
            "average" to "Priemer", "correction" to "Oprava:", "perfect" to "Perfektné! 🏆",
            "retry" to "ZNOVA", "menu" to "MENU", "wrong" to "Skús znova:", "skip" to "PRESKOČIŤ",
            "theme" to "Téma", "lang" to "Jazyk", "pause" to "Pauza", "resume" to "Pokračovať", "exit" to "Ukončiť"
        ) else mapOf(
            "title" to "Mathe Trainer", "start" to "START", "history" to "Trend & Verlauf:",
            "example" to "Aufgabe", "time" to "Zeit", "answer" to "Antwort", "confirm" to "BESTÄTIGEN",
            "results" to "📊 Ergebnisse", "success" to "Erfolg", "totalTime" to "Gesamtzeit",
            "average" to "Schnitt", "correction" to "Korrektur:", "perfect" to "Perfekt! 🏆",
            "retry" to "WIEDERHOLEN", "menu" to "MENÜ", "wrong" to "Versuch's nochmal:", "skip" to "ÜBERSPRINGEN",
            "theme" to "Modus", "lang" to "Sprache", "pause" to "Pause", "resume" to "Weiter", "exit" to "Beenden"
        )
    }

    // Herné stavy
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
    val progressValue = if(hraBezi && limit > 0) (aktualnyIndex.toFloat() / limit) else 0f

    // --- LOGIKA ---
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
                vibruj(context, "ok")
                bgFarba = if(isDark) Color(0xFF1B5E20) else Color(0xFFC8E6C9)
                if(!rezimOpravy) body++
                delay(350); bgFarba = Color.Transparent
                if (aktualnyIndex < limit - 1) {
                    aktualnyIndex++; generuj(); vstup = ""; rezimOpravy = false
                } else {
                    celkovyCas = System.currentTimeMillis() - startT; historiaKol.add(ZaznamKola(System.currentTimeMillis(), historiaKol.size+1, typX, body, limit, celkovyCas/1000.0)); zobrazVysledok = true
                }
            } else {
                vibruj(context, "err")
                bgFarba = if(isDark) Color(0xFFB71C1C) else Color(0xFFFFCDD2)
                delay(350); bgFarba = Color.Transparent; vstup = ""; rezimOpravy = true
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
                sekundy = ((System.currentTimeMillis() - start) / 1000).toInt(); delay(500)
            }
        }
    }

    // --- UI ---
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Mathe Trainer", Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Divider() // Opravené na Divider
                NavigationDrawerItem(label={Text(if(jePauza) t["resume"]!! else t["pause"]!!)}, icon={Icon(if(jePauza) Icons.Default.PlayArrow else Icons.Default.Pause, null)}, selected=false, onClick={jePauza=!jePauza; scope.launch{drawerState.close()}})
                NavigationDrawerItem(label={Text(t["lang"]!! + ": " + jazyk)}, icon={Icon(Icons.Default.Language, null)}, selected=false, onClick={jazyk = if(jazyk=="Slovenčina") "Deutsch" else "Slovenčina"})
                NavigationDrawerItem(label={Text(t["theme"]!! + ": " + themeMode.uppercase())}, icon={Icon(Icons.Default.BrightnessMedium, null)}, selected=false, onClick={onThemeToggle(if(themeMode=="light") "dark" else if(themeMode=="dark") "system" else "light")})
                NavigationDrawerItem(label={Text(t["exit"]!!)}, icon={Icon(Icons.Default.Close, null)}, selected=false, onClick={(context as? ComponentActivity)?.finish()})
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    CenterAlignedTopAppBar(title={Text(t["title"]!!, fontWeight=FontWeight.Black)}, navigationIcon={IconButton(onClick={scope.launch{drawerState.open()}}){Icon(Icons.Default.Menu, null)}})
                    if(hraBezi && !zobrazVysledok) {
                        LinearProgressIndicator(progress = progressValue, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            bottomBar = { Text("DCC © 2026 | All Rights Reserved", Modifier.fillMaxWidth().padding(8.dp), fontSize=9.sp, textAlign=TextAlign.Center, color=Color.Gray) }
        ) { p ->
            Box(Modifier.fillMaxSize().padding(p)) {
                when {
                    jePauza -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.PauseCircle, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary)
                        Text(t["resume"]!!, fontSize=20.sp); Button(onClick={jePauza=false}, Modifier.padding(16.dp)){Text(t["resume"]!!)}
                    }
                    !hraBezi -> Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        var exX by remember { mutableStateOf(false) }; var exP by remember { mutableStateOf(false) }
                        Text(t["limitX"]!!)
                        ExposedDropdownMenuBox(exX, {exX=!exX}){
                            OutlinedTextField(value = typX, onValueChange = {}, readOnly = true, trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded = exX)}, modifier=Modifier.menuAnchor().width(140.dp))
                            ExposedDropdownMenu(exX, {exX=false}){ (10..20).forEach{ i -> DropdownMenuItem(text={Text(i.toString())}, onClick={typX=i.toString(); exX=false}) } }
                        }
                        Spacer(Modifier.height(8.dp)); Text(t["limitP"]!!)
                        ExposedDropdownMenuBox(exP, {exP=!exP}){
                            OutlinedTextField(value = limit.toString(), onValueChange = {}, readOnly = true, trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded = exP)}, modifier=Modifier.menuAnchor().width(140.dp))
                            ExposedDropdownMenu(exP, {exP=false}){ listOf(5,10,15,20,30).forEach{ i -> DropdownMenuItem(text={Text(i.toString())}, onClick={limit=i; exP=false}) } }
                        }
                        if(historiaKol.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp)); Text(t["history"]!!, fontWeight=FontWeight.Bold)
                            TrendovyGraf(historiaKol, isDark)
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
                                listOf(t["type"]!! to 1f, "N" to 0.7f, t["time"]!! to 1.3f, "%" to 0.7f, "OK" to 0.7f).forEach { (txt, w) -> Text(txt, Modifier.weight(w), fontSize=10.sp, textAlign=TextAlign.Center, fontWeight=FontWeight.Bold) }
                            }
                            LazyColumn(Modifier.weight(1f).border(0.5.dp, Color.Gray)) {
                                items(historiaKol.reversed(), key={it.id}){ k ->
                                    val u = (k.spravne.toDouble()/k.celkovo*100).toInt()
                                    Row(Modifier.fillMaxWidth().padding(4.dp).border(0.5.dp, Color.LightGray)) {
                                        Text("1-${k.typX}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text("${k.celkovo}", Modifier.weight(0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text("${"%.1f".format(k.casSekundy)}s", Modifier.weight(1.3f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                        Text("$u%", Modifier.weight(0.7f), fontSize = 11.sp, textAlign = TextAlign.Center, color = if(u<50) Color.Red else MaterialTheme.colorScheme.primary)
                                        Text("${k.spravne}", Modifier.weight(0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        } else Spacer(Modifier.weight(1f))
                        Button({ startT=System.currentTimeMillis(); sekundy=0; body=0; aktualnyIndex=0; chyby.clear(); hraBezi=true; generuj() }, Modifier.fillMaxWidth().height(60.dp).padding(top=8.dp)){Text(t["start"]!!)}
                    }
                    zobrazVysledok -> StatistikaScreen(body, limit, celkovyCas, chyby, t, { hraBezi=false; zobrazVysledok=false })
                    else -> Column(Modifier.fillMaxSize().background(animBg).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${t["example"]} ${aktualnyIndex+1}/$limit | ${sekundy}s", color=Color.Gray)
                        Spacer(Modifier.height(40.dp))
                        Text(if(rezimOpravy) t["wrong"]!! else "$n1 × $n2 =", fontSize=if(rezimOpravy) 30.sp else 70.sp, fontWeight=FontWeight.ExtraBold, color=if(rezimOpravy) Color.Red else Color.Unspecified)
                        if(rezimOpravy) Text("$n1 × $n2 =", fontSize=70.sp, fontWeight=FontWeight.Black, color=Color.Gray)
                        Spacer(Modifier.height(30.dp))
                        OutlinedTextField(value = vstup, onValueChange = { if(it.all{c->c.isDigit()}){ vstup=it; if(rezimOpravy && it.length==(n1*n2).toString().length) potvrd(it) } }, label={Text(t["answer"]!!)}, modifier=Modifier.fillMaxWidth(), keyboardOptions=KeyboardOptions(keyboardType = KeyboardType.Number), trailingIcon={IconButton(onClick = { val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, if(jazyk=="Slovenčina") "sk-SK" else "de-DE") }; mic.launch(i) }){Icon(Icons.Default.Mic, null)}})
                        if(rezimOpravy) Row(Modifier.padding(top=20.dp)) {
                            OutlinedButton(onClick = { chyby.add(ChybnyPriklad(n1,n2)); if(aktualnyIndex<limit-1){aktualnyIndex++; generuj(); vstup=""; rezimOpravy=false} else {celkovyCas=System.currentTimeMillis()-startT; historiaKol.add(ZaznamKola(System.currentTimeMillis(), historiaKol.size+1, typX, body, limit, celkovyCas/1000.0)); zobrazVysledok=true} }, modifier = Modifier.weight(1f).height(55.dp)){Icon(Icons.Default.SkipNext, null); Text(t["skip"]!!)}
                            Button(onClick = {potvrd(vstup)}, modifier = Modifier.weight(1f).padding(start=8.dp).height(55.dp)){Text(t["confirm"]!!)}
                        } else Button(onClick = {potvrd(vstup)}, modifier = Modifier.fillMaxWidth().padding(top=20.dp).height(55.dp)){Text(t["confirm"]!!)}
                    }
                }
            }
        }
    }
}

@Composable
fun StatistikaScreen(ok: Int, tot: Int, ms: Long, errs: List<ChybnyPriklad>, t: Map<String, String>, onMenu: () -> Unit) {
    val sec = ms / 1000.0
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t["results"]!!, fontSize=30.sp, fontWeight=FontWeight.Bold)
        Text("${t["success"]}: $ok/$tot | ${t["totalTime"]}: ${"%.1f".format(sec)}s", fontSize=18.sp)
        Text("${t["average"]}: ${"%.2f".format(sec/tot)}s", color=MaterialTheme.colorScheme.primary)
        if(errs.isNotEmpty()){
            Spacer(Modifier.height(10.dp)); Text(t["correction"]!!, color=Color.Red, fontWeight=FontWeight.Bold)
            LazyColumn(Modifier.weight(1f)){ items(errs){ e -> ChybnyPrikladItem(e) } }
        } else { Spacer(Modifier.weight(1f)); Text(t["perfect"]!!, fontSize=26.sp, color=Color(0xFF388E3C), fontWeight=FontWeight.Bold) }
        Button(onClick = onMenu, modifier = Modifier.fillMaxWidth()){Text(t["menu"]!!)}
    }
}

@Composable
fun ChybnyPrikladItem(e: ChybnyPriklad) {
    var v by remember { mutableStateOf("") }; var done by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(4.dp), colors=CardDefaults.cardColors(containerColor = if(done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${e.c1} × ${e.c2} =", Modifier.width(100.dp))
            if(!done){
                OutlinedTextField(value = v, onValueChange = { if(it.all{c->c.isDigit()}){ v=it; if(it.toIntOrNull()==e.c1*e.c2) done=true } }, modifier = Modifier.width(80.dp), keyboardOptions=KeyboardOptions(keyboardType = KeyboardType.Number))
                IconButton(onClick = {if(v.toIntOrNull()==e.c1*e.c2) done=true}){Icon(Icons.Default.CheckCircle, null)}
            } else { Text("${e.c1*e.c2}", fontWeight=FontWeight.Bold); Spacer(Modifier.weight(1f)); Icon(Icons.Default.CheckCircle, null) }
        }
    }
}
