package com.monstro.v18

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.util.Locale

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFA855F7), background = Color(0xFF020306), surface = Color(0xFF121214)
            )) {
                val model: EditorModel = viewModel()
                DisposableEffect(model.exporting) {
                    if (model.exporting) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                EditorScreen(model)
            }
        }
    }
}

fun timeLabel(ms: Long): String = String.format(Locale.US, "%02d:%02d.%01d", ms / 60000, ms / 1000 % 60, ms / 100 % 10)

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun EditorScreen(model: EditorModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), model::importVideos)
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), model::importLyrics)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4"), model::saveOutput)
    var confirmStrobe by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.player.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("MONSTRO V18 · CHAOS FX", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Seu projeto é salvo automaticamente neste aparelho.", style = MaterialTheme.typography.bodySmall)
            if (model.current == null) {
                Surface(Modifier.fillMaxWidth().height(190.dp), shape = MaterialTheme.shapes.large) {
                    Box(contentAlignment = Alignment.Center) { Text("Importe vídeos para começar") }
                }
            } else {
                Text("Prévia do clipe ${model.selected + 1}", style = MaterialTheme.typography.labelLarge)
                // Give each decoder its own Surface. A surface previously owned by the GPU
                // effect processor cannot reliably be reused by a direct MediaCodec decoder.
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                    key(model.player) {
                        AndroidView(
                            factory = { context -> EditorPlayerView(context).apply { useController = true } },
                            update = { it.bind(model.player, !model.compatibilityPreview && previewEffects(model.current!!).isNotEmpty()) },
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    AndroidView(factory = { LyricsPreviewView(it) },
                        update = { it.model = model; it.invalidate() }, modifier = Modifier.matchParentSize())
                }
            }
            if (model.current != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prévia de compatibilidade")
                        Text(if (model.compatibilityPreview) "Sem efeitos na prévia. Efeitos mantidos no MP4."
                            else "Mostrar os efeitos na prévia.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = model.compatibilityPreview, onCheckedChange = { model.toggleCompatibilityPreview() }, enabled = !model.busy)
                }
            }
            Button(onClick = { picker.launch(arrayOf("video/*")) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (model.importing) "Importando…" else "+ Importar vídeos")
            }
            OutlinedButton(onClick = { srtPicker.launch(arrayOf("*/*")) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
                Text("+ Importar Legenda (.srt)")
            }
            model.lyrics?.let { track ->
                Text("Trap Lyrics FX · ${model.lyricsName} · ${track.cues.size} frases")
                Text("SRT da linha do tempo completa. Destaque por palavra aproximado pelo tempo da frase. Ajuste o SRT se mudar os cortes ou a ordem dos clipes.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = model.simpleLyrics, onCheckedChange = { model.toggleSimpleLyrics() }, enabled = !model.busy)
                    Text("Compatibilidade de legenda", Modifier.weight(1f))
                }
                Text("Compatibilidade reduz o tamanho da textura e desliga glow e pop-in; mantém texto e palavras no MP4.", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = model::toggleLyricsColor, enabled = !model.busy) { Text(if(model.purpleLyrics) "Cor: Roxo Neon" else "Cor: Vermelho Neon") }
                    TextButton(onClick = model::removeLyrics, enabled = !model.busy) { Text("Remover legenda") }
                }
            }
            if (model.clips.isNotEmpty()) {
                Text("${model.clips.size} clipe(s) • Total ${timeLabel(model.clips.sumOf { it.trim.duration })}")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(model.clips, key = { _, clip -> clip.id }) { index, clip ->
                        FilterChip(selected = index == model.selected, onClick = { model.select(index) }, enabled = !model.busy,
                            label = { Column(Modifier.widthIn(max = 145.dp)) {
                                Text("${index + 1}. ${clip.name}", maxLines = 1)
                                Text(timeLabel(clip.trim.duration), style = MaterialTheme.typography.labelSmall)
                            } })
                    }
                }
            }
            model.current?.let { clip ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { model.move(-1) }, enabled = !model.busy && model.selected > 0) { Text("← Mover") }
                    OutlinedButton(onClick = { model.move(1) }, enabled = !model.busy && model.selected < model.clips.lastIndex) { Text("Mover →") }
                    TextButton(onClick = model::remove, enabled = !model.busy) { Text("Excluir") }
                }
                var range by remember(clip.id, clip.trim) { mutableStateOf(clip.trim.start.toFloat()..clip.trim.end.toFloat()) }
                Text("Cortar: ${timeLabel(range.start.toLong())} até ${timeLabel(range.endInclusive.toLong())}")
                RangeSlider(value = range, onValueChange = { range = it },
                    valueRange = 0f..clip.duration.toFloat(), enabled = !model.busy,
                    onValueChangeFinished = {
                        val start = range.start.toLong().coerceIn(0, clip.duration - 1)
                        val end = range.endInclusive.toLong().coerceIn(start + 1, clip.duration)
                        model.edit(trim = TrimRange(start, end))
                    })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = model::split, enabled = !model.busy) { Text("Dividir na posição") }
                    TextButton(onClick = { model.edit(trim = TrimRange(0, clip.duration)) }, enabled = !model.busy) { Text("Restaurar corte") }
                }
                Text("Pause a prévia no ponto desejado para dividir.", style = MaterialTheme.typography.bodySmall)
                Text("Chaos FX", style = MaterialTheme.typography.titleMedium)
                Text("Combine os efeitos deste clipe. Eles também entram no MP4.", style = MaterialTheme.typography.bodySmall)
                ChaosFx.values().toList().chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { fx ->
                            FilterChip(modifier = Modifier.weight(1f), selected = clip.chaos.has(fx),
                                enabled = !model.busy, onClick = {
                                    if (fx == ChaosFx.STROBE && !clip.chaos.has(fx)) confirmStrobe = true
                                    else model.toggleFx(fx)
                                }, label = {
                                    Column(Modifier.padding(vertical = 6.dp)) {
                                        Text(fx.label, style = MaterialTheme.typography.labelLarge)
                                        Text(fx.description, style = MaterialTheme.typography.labelSmall)
                                    }
                                })
                        }
                    }
                }
                var zoom by remember(clip.id, clip.chaos.zoom) { mutableStateOf(clip.chaos.zoom) }
                Text("Master Zoom: " + String.format(Locale.US, "%.2fx", zoom))
                Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..3f, enabled = !model.busy,
                    onValueChangeFinished = { model.edit(chaos = clip.chaos.copy(zoom = zoom)) })
                TextButton(onClick = { model.edit(preset = "raw", chaos = ChaosSettings()) }, enabled = !model.busy) {
                    Text("Limpar efeitos e zoom deste clipe")
                }
                Text("Presets de cor", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("raw" to "Original", "neon" to "Neon", "trap" to "Trap Lord", "dark" to "Dark Energy", "cinema" to "Blockbuster").forEach { (id, label) ->
                            FilterChip(selected = clip.preset == id, onClick = { model.edit(preset = id) }, enabled = !model.busy,
                                label = { Text(label) })
                        }
                    } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = model.mute, onCheckedChange = { model.toggleMute() }, enabled = !model.busy)
                    Spacer(Modifier.width(8.dp)); Text("Remover áudio de todo o projeto")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = model.safeMode, onCheckedChange = { model.toggleSafeMode() }, enabled = !model.busy)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Modo leve")
                    Text(if(model.safeMode) "540p · até 30 fps · 2,5 Mbps" else "Alta qualidade · 1080p · até 30 fps · 10 Mbps", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (model.exporting) {
                Text(model.progress?.let { "Exportando: $it%" } ?: "Preparando exportação…")
                val progress = model.progress
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
                Text("Mantenha o aplicativo aberto até terminar.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = model::cancelExport) { Text("Cancelar exportação") }
            }
            Text("Exportação rápida", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { model.setExportFormat(true); model.export() }, enabled = !model.busy && model.clips.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Exportar 9:16 · Reels / TikTok")
            }
            OutlinedButton(onClick = { model.setExportFormat(false); model.export() }, enabled = !model.busy && model.clips.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Exportar 16:9 · MP4")
            }
            Text("Saída: ${model.exportFormat.width} × ${model.exportFormat.height}. Vídeo inteiro com fundo desfocado quando as proporções diferem. Legendas gravadas no MP4.", style = MaterialTheme.typography.bodySmall)
            if (model.output != null) {
                OutlinedButton(onClick = { saver.launch(model.output!!.name) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (model.saving) "Salvando…" else "Salvar último MP4 exportado")
                }
            }
        }
    }
    if (confirmStrobe) {
        AlertDialog(onDismissRequest = { confirmStrobe = false }, title = { Text("Ativar Psycho Strobe?") },
            text = { Text("Este efeito gera flashes de luz. Evite usar se você ou quem assistir tiver sensibilidade a flashes.") },
            confirmButton = { TextButton(onClick = { confirmStrobe = false; model.toggleFx(ChaosFx.STROBE) }) { Text("Ativar") } },
            dismissButton = { TextButton(onClick = { confirmStrobe = false }) { Text("Cancelar") } })
    }
    model.message?.let {
        AlertDialog(onDismissRequest = model::clearMessage, title = { Text("Monstro V18") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = model::clearMessage) { Text("OK") } })
    }
}
