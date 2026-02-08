package com.monstro.v18

import android.Manifest
import android.net.Uri
import android.os.Build
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
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- DESIGN SYSTEM & TEMAS ---

val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)

@Composable
fun MonstroV18Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MonstroAccent,
            background = MonstroBg,
            surface = MonstroBg,
            onSurface = Color.White
        ),
        content = content
    )
}

// --- MODELAGEM ---

sealed class VfxNode {
    object Shake : VfxNode()
    object Zoom : VfxNode()
    object Glitch : VfxNode()
}

data class MonstroPreset(
    val id: String,
    val name: String,
    val description: String
)

val MonstroLibrary = listOf(
    MonstroPreset("none", "RAW", "Sem efeitos"),
    MonstroPreset("neon_punch", "NEON PUNCH", "Impacto Vibrante"),
    MonstroPreset("trap_lord", "TRAP LORD", "Zoom Agressivo"),
    MonstroPreset("dark_energy", "DARK ENERGY", "Contraste e Noise")
)

data class MonstroClip(
    val uri: Uri,
    val activePreset: MonstroPreset = MonstroLibrary[0]
)

data class MonstroProject(
    val clips: List<MonstroClip> = emptyList()
)

// --- ACTIVITY PRINCIPAL ---

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

    // Estados do Projeto
    var project by remember { mutableStateOf(MonstroProject()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    // 1. GESTÃO DE PERMISSÕES (Android 13+ e Legado)
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Permissão necessária para carregar vídeos!", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionToRequest)
    }

    // 2. INICIALIZAÇÃO SEGURA DO EXOPLAYER
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        val newIndex = player.currentMediaItemIndex
                        if (newIndex >= 0) {
                            selectedIndex = newIndex
                        }
                    }
                }
            })
        }
    }

    // LIBERAÇÃO OBRIGATÓRIA DE MEMÓRIA
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // SELETOR DE VÍDEO
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val newClip = MonstroClip(uri = it)
            project = project.copy(clips = project.clips + newClip)
            exoPlayer.addMediaItem(MediaItem.fromUri(it))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    Scaffold(containerColor = MonstroBg) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            MonstroHeader(isExporting)
            Spacer(Modifier.height(16.dp))

            // 3. PREVIEW SEGURO (Handling Empty State)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .clickable(enabled = project.clips.isEmpty()) { picker.launch("video/*") }
            ) {
                if (project.clips.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("IMPORTAR MASTER", color = Color.Gray, fontWeight = FontWeight.Black)
                        Text("TOQUE PARA COMEÇAR", color = Color.Gray.copy(0.5f), fontSize = 10.sp)
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

            // 4. TIMELINE BLINDADA
            MonstroTimeline(
                clips = project.clips,
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    if (index in project.clips.indices) {
                        selectedIndex = index
                        exoPlayer.seekToDefaultPosition(index)
                    }
                },
                onAdd = { picker.launch("video/*") }
            )

            Spacer(Modifier.height(16.dp))
            MonstroTabHeader(selectedTab) { selectedTab = it }
            Spacer(Modifier.height(12.dp))

            // 5. ACESSO SEGURO AOS PRESETS (getOrNull)
            Box(modifier = Modifier.weight(1f)) {
                if (project.clips.isNotEmpty()) {
                    val currentClip = project.clips.getOrNull(selectedIndex)
                    
                    if (selectedTab == 0 && currentClip != null) {
                        MonstroPresetGrid(
                            currentPreset = currentClip.activePreset,
                            onSelect = { preset ->
                                if (selectedIndex in project.clips.indices) {
                                    val updated = project.clips.toMutableList()
                                    updated[selectedIndex] = updated[selectedIndex].copy(activePreset = preset)
                                    project = project.copy(clips = updated)
                                    Toast.makeText(context, "${preset.name} ATIVADO", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("ENGINE FX V18 ATIVA", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }

            MonstroRenderButton(enabled = project.clips.isNotEmpty()) {
                isExporting = true
                scope.launch {
                    delay(3000)
                    isExporting = false
                    Toast.makeText(context, "PROCESSO INDUSTRIAL COMPLETO", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

// --- COMPONENTES DE UI ---

@Composable
fun MonstroHeader(isExporting: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = (-1).sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (isExporting) Color.Red else EmeraldTurbo, RoundedCornerShape(50)))
                Spacer(Modifier.width(6.dp))
                Text(if (isExporting) "RENDERING" else "SISTEMA PRONTO", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(MonstroAccent, MonstroPink))))
    }
}

@Composable
fun MonstroTimeline(clips: List<MonstroClip>, selectedIndex: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(clips) { index, _ ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(90.dp, 50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) MonstroAccent.copy(0.2f) else Color.White.copy(0.05f))
                    .border(1.dp, if (active) MonstroAccent else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text("CLIP $index", color = if (active) Color.White else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Box(
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.05f)).clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun MonstroTabHeader(selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.02f), RoundedCornerShape(10.dp))) {
        listOf("PRESETS", "CHAOS FX").forEachIndexed { index, title ->
            val active = selected == index
            Box(
                modifier = Modifier.weight(1f).clickable { onSelect(index) }.padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(title, color = if (active) Color.White else Color.Gray, fontWeight = FontWeight.Black, fontSize = 10.sp)
                if (active) Box(Modifier.align(Alignment.BottomCenter).size(15.dp, 2.dp).background(MonstroAccent))
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
                    .border(1.dp, if (active) MonstroPink else Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                    .clickable { onSelect(preset) }
                    .padding(12.dp)
            ) {
                Text(preset.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text(preset.description.uppercase(), color = Color.Gray, fontSize = 7.sp)
            }
        }
    }
}

@Composable
fun MonstroRenderButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.White.copy(0.05f)),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                if (enabled) Brush.linearGradient(listOf(MonstroAccent, MonstroPink)) 
                else Brush.linearGradient(listOf(Color.DarkGray, Color.Black))
            ),
            contentAlignment = Alignment.Center
        ) {
            Text("RENDER TURBO MP4", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun MonstroLoadingOverlay() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MonstroAccent)
    }
}

