import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- CAMADA 1: O MOTOR DE PRESETS (ESTILO CAPCUT) ---

sealed class VfxNode {
    data class Shake(val intensity: Float) : VfxNode()
    data class Zoom(val scale: Float) : VfxNode()
    data class RgbSplit(val offset: Float) : VfxNode()
    object Glitch : VfxNode()
}

data class MonstroPreset(
    val id: String,
    val name: String,
    val description: String,
    val stack: List<VfxNode>
)

// Presets Oficiais da Biblioteca Monstro
val MonstroLibrary = listOf(
    MonstroPreset("none", "RAW", "Sem efeitos", emptyList()),
    MonstroPreset("neon_punch", "NEON PUNCH", "Impacto Vibrante", listOf(VfxNode.Shake(0.8f), VfxNode.RgbSplit(0.1f))),
    MonstroPreset("trap_lord", "TRAP LORD", "Zoom Agressivo", listOf(VfxNode.Zoom(1.2f), VfxNode.Glitch)),
    MonstroPreset("dark_energy", "DARK ENERGY", "Contraste e Noise", listOf(VfxNode.RgbSplit(0.3f), VfxNode.Shake(0.4f)))
)

// --- CAMADA 2: ESTRUTURA DE PROJETO ---

data class MonstroClip(
    val uri: Uri,
    var durationMs: Long = 0,
    val activePreset: MonstroPreset = MonstroLibrary[0]
)

data class MonstroProject(
    val clips: List<MonstroClip> = emptyList()
)

// --- DESIGN SYSTEM ---
val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MonstroV18Theme {
                MonstroIndustrialEditor()
            }
        }
    }
}

@Composable
fun MonstroIndustrialEditor() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State Management
    var project by remember { mutableStateOf(MonstroProject()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(1L) }
    var isExporting by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Chaos, 1: Presets

    // ExoPlayer Init
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        selectedIndex = player.currentMediaItemIndex
                        duration = player.duration.coerceAtLeast(1L)
                    }
                }
            })
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val newClip = MonstroClip(uri = it)
            project = project.copy(clips = project.clips + newClip)
            exoPlayer.addMediaItem(MediaItem.fromUri(it))
            exoPlayer.prepare()
        }
    }

    // Sync Loops
    LaunchedEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
        while (isPlaying && exoPlayer.isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(33)
        }
    }

    Scaffold(containerColor = MonstroBg) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            // HEADER
            MonstroHeader(isExporting)

            Spacer(Modifier.height(16.dp))

            // PREVIEW (GPU MOCK)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                if (project.clips.isEmpty()) {
                    Box(Modifier.fillMaxSize().clickable { picker.launch("video/*") }, contentAlignment = Alignment.Center) {
                        Text("IMPORTAR MASTER", color = Color.Gray, fontWeight = FontWeight.Black)
                    }
                } else {
                    AndroidView(
                        factory = { PlayerView(it).apply { player = exoPlayer; useController = false } },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                if (isExporting) MonstroLoadingOverlay()
            }

            Spacer(Modifier.height(16.dp))

            // TIMELINE MULTI-CLIP
            MonstroTimeline(
                clips = project.clips,
                selectedIndex = selectedIndex,
                onSelect = { 
                    selectedIndex = it
                    exoPlayer.seekTo(it, 0)
                },
                onAdd = { picker.launch("video/*") }
            )

            Spacer(Modifier.height(16.dp))

            // TAB SYSTEM (CHAOS VS PRESETS)
            MonstroTabHeader(selectedTab) { selectedTab = it }

            Spacer(Modifier.height(12.dp))

            // PAINEL DINÂMICO
            Box(modifier = Modifier.weight(1f)) {
                if (project.clips.isNotEmpty()) {
                    if (selectedTab == 0) {
                        // Painel de Presets (A alma do app)
                        MonstroPresetGrid(
                            currentPreset = project.clips[selectedIndex].activePreset,
                            onSelect = { preset ->
                                val updated = project.clips.toMutableList()
                                updated[selectedIndex] = updated[selectedIndex].copy(activePreset = preset)
                                project = project.copy(clips = updated)
                                // Aqui dispararia o re-bind do shader no OpenGL
                                Toast.makeText(context, "${preset.name} Aplicado!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        // Chaos FX (Toggles isolados)
                        Text("Nodos isolados em construção...", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }

            // RENDER BUTTON
            MonstroRenderButton(enabled = project.clips.isNotEmpty()) {
                isExporting = true
                scope.launch {
                    delay(3000) // Simulação do Transformer
                    isExporting = false
                    Toast.makeText(context, "RENDER INDUSTRIAL COMPLETO", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun MonstroHeader(isExporting: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = (-1).sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (isExporting) Color.Red else EmeraldTurbo, RoundedCornerShape(50)))
                Spacer(Modifier.width(6.dp))
                Text(if (isExporting) "RENDERING STREAM" else "TURBO READY", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier.size(45.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(MonstroAccent, MonstroPink)))
        )
    }
}

@Composable
fun MonstroTimeline(clips: List<MonstroClip>, selectedIndex: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(clips) { index, _ ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(100.dp, 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MonstroAccent.copy(0.3f) else Color.White.copy(0.05f))
                    .border(2.dp, if (active) MonstroAccent else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text("CLIP ${index + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
        item {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.05f)).clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun MonstroTabHeader(selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.03f), RoundedCornerShape(12.dp))) {
        listOf("PRESETS", "CHAOS FX").forEachIndexed { index, title ->
            val active = selected == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title, 
                    color = if (active) Color.White else Color.Gray, 
                    fontWeight = FontWeight.Black, 
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                if (active) {
                    Box(Modifier.align(Alignment.BottomCenter).size(20.dp, 2.dp).background(MonstroAccent))
                }
            }
        }
    }
}

@Composable
fun MonstroPresetGrid(currentPreset: MonstroPreset, onSelect: (MonstroPreset) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(MonstroLibrary) { preset ->
            val active = currentPreset.id == preset.id
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MonstroPink.copy(0.1f) else Color.White.copy(0.02f))
                    .border(1.dp, if (active) MonstroPink else Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                    .clickable { onSelect(preset) }
                    .padding(16.dp)
            ) {
                Text(preset.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text(preset.description.uppercase(), color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MonstroRenderButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.White.copy(0.05f)),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                if (enabled) Brush.linearGradient(listOf(MonstroAccent, MonstroPink)) 
                else Brush.linearGradient(listOf(Color.Gray, Color.DarkGray))
            ),
            contentAlignment = Alignment.Center
        ) {
            Text("RENDER TURBO MP4", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun MonstroLoadingOverlay() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MonstroAccent)
            Spacer(Modifier.height(12.dp))
            Text("PROCESSANDO GPU NODES...", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun MonstroV18Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = MonstroAccent), content = content)
}
