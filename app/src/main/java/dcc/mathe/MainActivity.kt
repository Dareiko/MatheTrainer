package dcc.mathe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    var cislo1 by remember { mutableIntStateOf((1..12).random()) }
    var cislo2 by remember { mutableIntStateOf((1..12).random()) }
    var vstupPouzivatela by remember { mutableStateOf("") }
    var pocetPrikladov by remember { mutableIntStateOf(0) }
    var spravneOdpovede by remember { mutableIntStateOf(0) }
    var startTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var celkovyCas by remember { mutableLongStateOf(0L) }
    var zobrazVysledok by remember { mutableStateOf(false) }
    
    val jazyky = listOf("Slovenčina", "Deutsch")
    var zvolenyJazyk by remember { mutableStateOf(jazyky[0]) }
    var farbaPozadia by remember { mutableStateOf(Color.Transparent) }
    val animovanaFarba by animateColorAsState(targetValue = farbaPozadia, label = "")
    val scope = rememberCoroutineScope()

    if (zobrazVysledok) {
        StatistikaScreen(spravneOdpovede, celkovyCas) {
            pocetPrikladov = 0; spravneOdpovede = 0; celkovyCas = 0
            zobrazVysledok = false; startTime = System.currentTimeMillis()
            cislo1 = (1..12).random(); cislo2 = (1..12).random()
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().background(animovanaFarba).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Jazyk / Sprache:", fontWeight = FontWeight.Bold)
            Row(Modifier.padding(8.dp)) {
                jazyky.forEach { text ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.selectable(
                            selected = (text == zvolenyJazyk),
                            onClick = { zvolenyJazyk = text }
                        ).padding(horizontal = 8.dp)
                    ) {
                        RadioButton(selected = (text == zvolenyJazyk), onClick = null)
                        Text(text = text, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            Text(text = "Príklad ${pocetPrikladov + 1} / 5", fontSize = 20.sp)
            Text(text = "$cislo1 × $cislo2 =", fontSize = 64.sp, fontWeight = FontWeight.Black)
            
            OutlinedTextField(
                value = vstupPouzivatela,
                onValueChange = { if (it.all { c -> c.isDigit() }) vstupPouzivatela = it },
                label = { Text(if (zvolenyJazyk == "Slovenčina") "Odpoveď" else "Antwort") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val odpoved = vstupPouzivatela.toIntOrNull()
                    val jeSpravne = odpoved == cislo1 * cislo2
                    celkovyCas += (System.currentTimeMillis() - startTime)

                    scope.launch {
                        farbaPozadia = if (jeSpravne) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
                        if (jeSpravne) spravneOdpovede++
                        delay(400)
                        farbaPozadia = Color.Transparent
                        
                        if (pocetPrikladov < 4) {
                            pocetPrikladov++
                            cislo1 = (1..12).random()
                            cislo2 = (1..12).random()
                            vstupPouzivatela = ""
                            startTime = System.currentTimeMillis()
                        } else {
                            zobrazVysledok = true
                        }
                    }
                },
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(56.dp)
            ) {
                Text(if (zvolenyJazyk == "Slovenčina") "POTVRDIŤ" else "BESTÄTIGEN")
            }
        }
    }
}

@Composable
fun StatistikaScreen(spravne: Int, cas: Long, onRestart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📊 Výsledky", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("Úspešnosť: $spravne / 5", fontSize = 20.sp)
        Text("Celkový čas: ${cas / 1000} s", fontSize = 20.sp)
        Text("Priemer: ${"%.2f".format((cas / 5.0) / 1000.0)} s / príklad", fontSize = 16.sp)
        Spacer(Modifier.height(40.dp))
        Button(onClick = onRestart) { Text("HRAŤ ZNOVA") }
    }
}