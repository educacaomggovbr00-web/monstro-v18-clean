package com.monstro.v18

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import androidx.media3.transformer.Composition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import android.content.SharedPreferences
import com.monstro.v18.clearcut.*

// Milliseconds throughout; ranges must always contain at least one millisecond.
data class TrimRange(val start: Long, val end: Long) {
    init { require(start >= 0 && end > start) }
    val duration: Long get() = end - start
    fun split(offset: Long): Pair<TrimRange, TrimRange>? {
        if (offset <= 0 || offset >= duration) return null
        return TrimRange(start, start + offset) to TrimRange(start + offset, end)
    }
}

data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val duration: Long,
    val trim: TrimRange = TrimRange(0, duration),
    val preset: String = "raw",
    val chaos: ChaosSettings = ChaosSettings(),
    val volume: Float = 1f,
    val mirror: Boolean = false
)

@UnstableApi
fun videoEffects(preset: String): List<Effect> = if (preset == "raw") emptyList() else listOf(ColorPreset(preset))

// Preview only applies visual effects. Frame dropping and output sizing belong to export.
@UnstableApi
fun previewEffects(clip: VideoClip): List<Effect> = videoEffects(clip.preset) +
    (if(clip.mirror) listOf(MirrorEffect()) else emptyList()) +
    (if (clip.chaos.isIdentity) emptyList() else listOf(ChaosEffect(clip.chaos)))

@UnstableApi
fun buildClipEffects(clip: VideoClip, safeMode: Boolean): List<Effect> = listOf(
    FrameDropEffect.createDefaultFrameDropEffect(if (safeMode) 30f else 60f),
    Presentation.createForHeight(if (safeMode) 480 else 720)
) + previewEffects(clip)

@UnstableApi
fun VideoClip.mediaItem(): MediaItem = MediaItem.Builder().setUri(uri)
    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(trim.start).setEndPositionMs(trim.end).build())
    .build()

@UnstableApi
class EditorModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("editor", 0)
    private val projectStore = ProjectStore(File(context.filesDir, "projects"))
    var savedProjects by mutableStateOf<List<SavedProject>>(emptyList()); private set
    var trashedProjects by mutableStateOf<List<SavedProject>>(emptyList()); private set
    var projectName by mutableStateOf("Projeto Monstro"); private set
    var projectBusy by mutableStateOf(false); private set
    private var activeProjectId = prefs.getString("activeProjectId", null)
    private var projectSaveJob: Job? = null
    private var applyingProject = false
    private val projectPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!applyingProject && key != "activeProjectId") scheduleProjectSave()
    }
    private data class EditorSnapshot(
        val clips:List<VideoClip>,val selected:Int,val mute:Boolean,val studio:StudioProject,
        val lyrics:SrtTrack?,val lyricsName:String,val simpleLyrics:Boolean,val purpleLyrics:Boolean
    )
    private val undoStack=java.util.ArrayDeque<EditorSnapshot>()
    private val redoStack=java.util.ArrayDeque<EditorSnapshot>()
    private var historyVersion by mutableStateOf(0)
    val canUndo get()=historyVersion.let {undoStack.isNotEmpty()}
    val canRedo get()=historyVersion.let {redoStack.isNotEmpty()}
    private fun snapshot()=EditorSnapshot(clips,selected,mute,studio,lyrics,lyricsName,simpleLyrics,purpleLyrics)
    private fun pushHistory(){
        undoStack.addLast(snapshot());while(undoStack.size>40)undoStack.removeFirst()
        redoStack.clear();historyVersion++
    }
    private fun restoreSnapshot(s:EditorSnapshot){
        clips=s.clips;selected=s.selected.coerceIn(0,(clips.size-1).coerceAtLeast(0));mute=s.mute;studio=s.studio
        lyrics=s.lyrics;lyricsName=s.lyricsName;simpleLyrics=s.simpleLyrics;purpleLyrics=s.purpleLyrics
        persist();persistCues()
        prefs.edit().putString("studio",StudioCodec.encode(studio)).putString("lyricsName",lyricsName)
            .putBoolean("simpleLyrics",simpleLyrics).putBoolean("purpleLyrics",purpleLyrics).apply()
        preview()
    }
    fun undo(){if(busy || undoStack.isEmpty())return;pauseAll();redoStack.addLast(snapshot());restoreSnapshot(undoStack.removeLast());historyVersion++}
    fun redo(){if(busy || redoStack.isEmpty())return;pauseAll();undoStack.addLast(snapshot());restoreSnapshot(redoStack.removeLast());historyVersion++}
    var clips by mutableStateOf<List<VideoClip>>(emptyList()); private set
    var selected by mutableStateOf(0); private set
    var mute by mutableStateOf(false); private set
    var safeMode by mutableStateOf(true); private set
    var canvasRatio by mutableStateOf(prefs.getString("canvasRatio",if(prefs.getBoolean("vertical",true))"9:16" else "16:9") ?: "9:16"); private set
    var canvasBackground by mutableStateOf(prefs.getString("canvasBackground","blur") ?: "blur"); private set
    var bitrateMode by mutableStateOf(prefs.getString("bitrateMode","recommended") ?: "recommended"); private set
    var exportCodec by mutableStateOf(prefs.getString("exportCodec","H264") ?: "H264"); private set
    var exportFps by mutableStateOf(prefs.getInt("exportFps",30).takeIf {it in listOf(24,30,60)} ?: 30); private set
    var vertical by mutableStateOf(canvasRatio=="9:16" || canvasRatio=="4:5"); private set
    var lyrics by mutableStateOf<SrtTrack?>(null); private set
    var lyricsName by mutableStateOf(""); private set
    var simpleLyrics by mutableStateOf(false); private set
    var purpleLyrics by mutableStateOf(true); private set
    val exportFormat get() = ExportFormat(canvasRatio,safeMode,canvasBackground,bitrateMode,exportCodec,exportFps)
    val hevcSupported get() = runCatching { android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info->info.isEncoder && info.supportedTypes.any { it.equals(MimeTypes.VIDEO_H265,true) } } }.getOrDefault(false)
    val timelineOffset get() = clips.take(selected).sumOf { speedMap(it).outputDuration }

    var importing by mutableStateOf(false); private set
    var exporting by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var progress by mutableStateOf<Int?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var output by mutableStateOf<File?>(null); private set
    val busy get() = projectBusy || importing || exporting || saving || speechBusy || translationBusy || ttsBusy || beatBusy || aiEditBusy
    val current get() = clips.getOrNull(selected)
    var player by mutableStateOf(createPlayer()); private set

    var studio by mutableStateOf(runCatching { StudioCodec.decode(prefs.getString("studio","{}")!!) }.getOrDefault(StudioProject())); private set
    var inspector by mutableStateOf("Vídeo"); private set
    var focusedId by mutableStateOf(""); private set
    var playhead by mutableStateOf(0L); private set
    @Volatile private var shaderPlayhead=0L
    var speechStatus by mutableStateOf(""); private set
    var speechProgress by mutableStateOf<Int?>(null); private set
    var speechBusy by mutableStateOf(false); private set
    var translationBusy by mutableStateOf(false); private set
    var translationStatus by mutableStateOf(""); private set
    var translationProgress by mutableStateOf(0); private set
    var ttsBusy by mutableStateOf(false); private set
    var ttsStatus by mutableStateOf(""); private set
    var voiceoverRecording by mutableStateOf(false); private set
    var voiceoverStatus by mutableStateOf(""); private set
    var beatBusy by mutableStateOf(false); private set
    var beatStatus by mutableStateOf(""); private set
    var beatProgress by mutableStateOf(0); private set
    var aiEditBusy by mutableStateOf(false); private set
    var aiEditStatus by mutableStateOf(""); private set
    var aiEditProgress by mutableStateOf(0); private set
    private var voiceoverRecorder:MediaRecorder?=null
    private var voiceoverFile:File?=null
    private var voiceoverStart=0L
    var modelReady by mutableStateOf(AutoCaptions(context).ready); private set
    var captionEngine by mutableStateOf(prefs.getString("captionEngine","gemini") ?: "gemini"); private set
    val geminiReady get()=GeminiAudioCaptions(context).configured
    fun selectCaptionEngine(engine:String){if(engine !in listOf("gemini","offline") || busy)return;captionEngine=engine;prefs.edit().putString("captionEngine",engine).apply()}
    private var speechJob:Job?=null
    private var translationJob:Job?=null
    private var ttsJob:Job?=null
    private var beatJob:Job?=null
    private var aiEditJob:Job?=null
    private val audioPlayers=mutableMapOf<String,ExoPlayer>()
    fun speedMap(clip:VideoClip)=SpeedMap(clip.trim.duration,studio.motions[clip.id]?.speed ?: emptyList())
    val totalDuration get()=clips.sumOf { speedMap(it).outputDuration }
    val videoPerception get()=VideoPerceptionAnalyzer.analyze(clips,studio,lyrics,canvasRatio)
    fun focus(kind:String,id:String=""){inspector=kind;focusedId=id}
    fun runAiAutoEdit(style:String){
        if(busy || clips.isEmpty())return
        val detected=VideoPerceptionAnalyzer.analyze(clips,studio,lyrics,canvasRatio)
        if(!GeminiAutoEdit(context).configured){
            val local=LocalAutoEdit.build(clips,studio,style,lyrics,canvasRatio);applyAiEditPlan(local)
            aiEditStatus="Modo local · ${local.summary}"
            message="Detectei ${detected.label}. A IA online não está disponível neste APK, então apliquei a edição local adaptativa."
            return
        }
        pauseAll();aiEditBusy=true;aiEditProgress=0;aiEditStatus="${detected.label} · preparando análise…"
        aiEditJob=viewModelScope.launch {
            var contextCaptions=lyrics
            val generatedCaptions=contextCaptions==null
            try{
                if(contextCaptions==null && modelReady){
                    try{
                        aiEditStatus="${detected.label} · entendendo a fala no aparelho…"
                        contextCaptions=withContext(Dispatchers.IO){
                            AutoCaptions(context).transcribe(clips,studio){p->
                                viewModelScope.launch {
                                    aiEditProgress=(p*25/100).coerceIn(0,25)
                                    aiEditStatus="Fala local · $p%"
                                }
                            }
                        }
                    }catch(e:Exception){
                        if(e is kotlinx.coroutines.CancellationException)throw e
                        contextCaptions=null
                        aiEditStatus="${detected.label} · seguindo pela análise visual…"
                    }
                }
                val profile=VideoPerceptionAnalyzer.analyze(clips,studio,contextCaptions,canvasRatio)
                val plan=withContext(Dispatchers.IO){
                    GeminiAutoEdit(context).analyze(clips,studio,contextCaptions,style,{p->
                        viewModelScope.launch {
                            val base=if(generatedCaptions && modelReady)25 else 0
                            val mapped=base+p*(100-base)/100
                            aiEditProgress=mapped.coerceIn(0,100)
                            aiEditStatus=when {
                                p<30 -> "${profile.label} · entendendo os quadros…"
                                p<55 -> "Cruzando cenas, fala e beats…"
                                p<90 -> "Gemini montando uma edição limpa…"
                                else -> "Aplicando a edição…"
                            }
                        }
                    },canvasRatio)
                }
                applyAiEditPlan(plan)
                if(generatedCaptions && contextCaptions!=null){
                    lyrics=contextCaptions;lyricsName="Legendas locais · Auto Edit";persistCues()
                }
                aiEditStatus="Pronto · ${plan.summary}"
                message="Auto Edit concluído · ${profile.label}. Você pode desfazer tudo com ↶."
            }catch(e:Exception){
                if(e is kotlinx.coroutines.CancellationException){
                    aiEditStatus="IA Auto Edit cancelado"
                }else{
                    val local=LocalAutoEdit.build(clips,studio,style,contextCaptions,canvasRatio)
                    applyAiEditPlan(local)
                    aiEditProgress=100
                    aiEditStatus="Modo local · ${local.summary}"
                    message=GeminiSupport.userMessage(e)+" Continuei com o Auto Edit local, sem perder o projeto."
                }
            }finally{aiEditBusy=false}
        }
    }
        fun cancelAiAutoEdit(){aiEditJob?.cancel()}
    private fun applyAiEditPlan(plan:AiEditPlan){
        if(clips.isEmpty())return
        pushHistory()
        var nextStudio=studio
        val nextClips=clips.toMutableList()

        plan.looks.forEach {look->
            val clip=nextClips.getOrNull(look.clipIndex) ?: return@forEach
            nextClips[look.clipIndex]=clip.copy(preset=look.preset)
            val base=nextStudio.adjustments[clip.id] ?: ClipAdjust()
            nextStudio=nextStudio.copy(adjustments=nextStudio.adjustments+(clip.id to base.copy(
                brightness=look.brightness,contrast=look.contrast,saturation=look.saturation,temperature=look.temperature
            )))
        }

        plan.motions.groupBy {it.clipIndex}.forEach {(index,keys)->
            val clip=nextClips.getOrNull(index) ?: return@forEach
            var motion=nextStudio.motions[clip.id] ?: ClipMotion()
            keys.sortedBy {it.timeMs}.forEach {k->
                motion=motion.copy(
                    zoom=putKey(motion.zoom,k.timeMs,k.zoom),
                    x=putKey(motion.x,k.timeMs,k.x),
                    y=putKey(motion.y,k.timeMs,k.y),
                    rotation=putKey(motion.rotation,k.timeMs,k.rotation)
                )
            }
            nextStudio=nextStudio.copy(motions=nextStudio.motions+(clip.id to motion))
        }

        plan.speeds.groupBy {it.clipIndex}.forEach {(index,keys)->
            val clip=nextClips.getOrNull(index) ?: return@forEach
            var motion=nextStudio.motions[clip.id] ?: ClipMotion()
            keys.sortedBy {it.timeMs}.forEach {k->motion=motion.copy(speed=putKey(motion.speed,k.timeMs,k.speed))}
            nextStudio=nextStudio.copy(motions=nextStudio.motions+(clip.id to motion))
        }

        val aiFx=plan.effects.mapNotNull {suggestion->
            val choices=FxCatalog.search("",suggestion.category)
            val preset=choices.getOrNull(if(choices.isEmpty())0 else suggestion.variant%choices.size) ?: return@mapNotNull null
            FxLayer(
                presetId=preset.id,start=suggestion.startMs.coerceIn(0,totalDuration),
                end=suggestion.endMs.coerceIn(0,totalDuration),
                intensity=suggestion.intensity
            ).takeIf {it.end>it.start}
        }
        if(aiFx.isNotEmpty())nextStudio=nextStudio.copy(
            fx=nextStudio.fx+aiFx,
            recent=(aiFx.map {it.presetId}+nextStudio.recent).distinct().take(30)
        )

        val existing=nextStudio.markers.toMutableList()
        plan.markers.forEach {mark->
            if(existing.none {kotlin.math.abs(it.time-mark.timeMs)<80 && it.label==mark.label})
                existing+=TimelineMarker(time=mark.timeMs.coerceIn(0,totalDuration),label="IA · ${mark.label}")
        }
        nextStudio=nextStudio.copy(markers=existing.sortedBy {it.time})

        clips=nextClips
        studio=nextStudio
        canvasRatio=plan.ratio
        vertical=canvasRatio=="9:16" || canvasRatio=="4:5"
        prefs.edit()
            .putString("studio",StudioCodec.encode(studio))
            .putString("canvasRatio",canvasRatio)
            .putBoolean("vertical",vertical)
            .apply()
        persist()
        preview()
    }
    fun addTimelineMarker(){
        if(busy || totalDuration<=0)return
        val time=playhead.coerceIn(0,totalDuration)
        val near=studio.markers.any {kotlin.math.abs(it.time-time)<80}
        if(near){message="Já existe um marcador muito perto deste ponto.";return}
        val number=studio.markers.size+1
        updateStudio(studio.copy(markers=(studio.markers+TimelineMarker(time=time,label="M$number")).sortedBy {it.time}),false)
    }
    fun removeNearestMarker(){
        if(busy || studio.markers.isEmpty())return
        val target=studio.markers.minByOrNull {kotlin.math.abs(it.time-playhead)} ?: return
        if(kotlin.math.abs(target.time-playhead)>800){message="Mova o playhead perto de um marcador para remover.";return}
        updateStudio(studio.copy(markers=studio.markers-target),false)
    }
    fun importTimecodeMarkers(text:String) {
        if(busy || totalDuration<=0)return
        if(text.length>64000){message="Lista muito grande (máximo 64 mil caracteres).";return}
        val parsed=CutListParser.parse(text)
        if(parsed.hasErrors){message=parsed.errors.take(3).joinToString("\n"){"Linha ${it.lineNumber}: ${it.message}"};return}
        val points=parsed.entries.flatMap {e->
            if(e.endMs==null)listOf(TimelineMarker(time=e.startMs,label=e.label.ifBlank {"Marcador"}))
            else listOf(TimelineMarker(time=e.startMs,label=e.label.ifBlank {"Trecho"}+" · início"),TimelineMarker(time=e.endMs,label=e.label.ifBlank {"Trecho"}+" · fim"))
        }
        if(points.any {it.time !in 0..totalDuration} || points.size+studio.markers.size>5000){message="Use tempos dentro da timeline e no máximo 5.000 marcadores.";return}
        if(points.isEmpty()){message="Digite um timecode por linha, como 00:00:10.500 Intro.";return}
        updateStudio(studio.copy(markers=(studio.markers+points).sortedBy {it.time}),false)
        message="${points.size} marcadores adicionados. Desfazer recupera a versão anterior."
    }

    fun exportDiagnostics(uri:Uri?) {
        if(uri==null || busy)return
        saving=true
        viewModelScope.launch {
            try {withContext(Dispatchers.IO){
                val crashes=CrashRecordStore(context).buildDiagnosticJson()
                val report=JSONObject().put("version","18.6-Studio").put("sdk",Build.VERSION.SDK_INT)
                    .put("clips",clips.size).put("timelineDurationMs",totalDuration)
                    .put("crashes",crashes?.let {JSONObject(it)} ?: JSONObject())
                context.contentResolver.openOutputStream(uri,"wt")!!.use {it.write(report.toString(2).toByteArray(Charsets.UTF_8))}
            };message="Diagnóstico local salvo. Nenhuma mídia foi incluída."
            }catch(e:Exception){message="Não foi possível salvar o diagnóstico: ${e.localizedMessage}"}
            finally{saving=false}
        }
    }
    fun updateStudio(next:StudioProject,rebuild:Boolean=true,record:Boolean=true){if(busy || next==studio)return;if(record)pushHistory();studio=next;prefs.edit().putString("studio",StudioCodec.encode(next)).apply();if(rebuild){val pos=player.currentPosition;preview(pos,player.playWhenReady)}}
    fun seekTimeline(time:Long){if(clips.isEmpty() || busy)return;var remaining=time.coerceIn(0,(totalDuration-1).coerceAtLeast(0));var index=0
        while(index<clips.lastIndex && remaining>=speedMap(clips[index]).outputDuration){remaining-=speedMap(clips[index]).outputDuration;index++}
        val source=speedMap(clips[index]).toSource(remaining);if(index!=selected){selected=index;preview(source,player.playWhenReady)}else player.seekTo(source)
        playhead=time.coerceIn(0,totalDuration);shaderPlayhead=playhead;syncAudio()
    }
    fun tick(){val clip=current ?: return;val map=speedMap(clip);playhead=timelineOffset+map.toOutput(player.currentPosition);shaderPlayhead=playhead
        val speed=map.speedAt(player.currentPosition);if(kotlin.math.abs(player.playbackParameters.speed-speed)>.005f)player.setPlaybackSpeed(speed)
        syncAudio()
    }
    private fun syncAudio(){
        val ids=studio.audio.map {it.id}.toSet();audioPlayers.keys.toList().filter {it !in ids}.forEach {audioPlayers.remove(it)?.release()}
        studio.audio.forEach { layer->
            val active=playhead>=layer.start && playhead<layer.end && playhead<totalDuration
            val p=audioPlayers[layer.id] ?: if(active) ExoPlayer.Builder(context).build().also {p->p.setMediaItem(MediaItem.fromUri(layer.uri));p.prepare();audioPlayers[layer.id]=p} else return@forEach
            p.setPlaybackParameters(androidx.media3.common.PlaybackParameters(1f,layer.pitch.coerceIn(.5f,2f)))
            if(active){
                val local=playhead-layer.start;val duration=(layer.end-layer.start).coerceAtLeast(1)
                val inGain=if(layer.fadeIn<=0)1f else (local.toFloat()/layer.fadeIn).coerceIn(0f,1f)
                val outGain=if(layer.fadeOut<=0)1f else ((duration-local).toFloat()/layer.fadeOut).coerceIn(0f,1f)
                p.volume=(layer.volume*minOf(inGain,outGain)).coerceIn(0f,1f)
                val target=layer.trimStart+local;if(kotlin.math.abs(p.currentPosition-target)>150)p.seekTo(target);p.playWhenReady=player.isPlaying && !busy
            }else {p.volume=layer.volume.coerceIn(0f,1f);p.pause()}
        }
    }
    fun pauseAll(){player.pause();audioPlayers.values.forEach {it.pause()}}
    fun setMotion(motion:ClipMotion){val clip=current ?: return;updateStudio(studio.copy(motions=studio.motions+(clip.id to motion)))}
    fun addText(){val start=if(playhead>=totalDuration)(totalDuration-3000).coerceAtLeast(0)else playhead;val end=minOf(totalDuration,start+3000);if(end<=start)return;val layer=TextLayer(start=start,end=end);updateStudio(studio.copy(texts=studio.texts+layer),false);focus("Texto",layer.id)}
    fun generateNarration(text:String,voice:String,style:String){
        if(busy || clips.isEmpty())return
        if(!GeminiTts(context).configured){message="Firebase AI Logic ainda não está disponível neste APK.";return}
        val start=playhead.coerceIn(0,(totalDuration-1).coerceAtLeast(0))
        pauseAll();ttsBusy=true;ttsStatus="Gemini está criando a narração…"
        ttsJob=viewModelScope.launch {
            try {
                val generated=withContext(Dispatchers.IO){GeminiTts(context).generate(text,voice,style)}
                val available=(totalDuration-start).coerceAtLeast(1)
                val end=minOf(generated.durationMs,available).coerceAtLeast(1)
                ttsBusy=false
                val layer=AudioLayer(uri=generated.file.toURI().toString(),name="Narração IA · $voice",duration=generated.durationMs,start=start,trimEnd=end)
                updateStudio(studio.copy(audio=studio.audio+layer),false)
                focus("Áudio",layer.id);ttsStatus="Narração pronta · ${timeLabel(end)}"
            } catch(e:Exception) {
                ttsStatus=if(e is kotlinx.coroutines.CancellationException)"Narração cancelada" else GeminiSupport.userMessage(e)
            } finally {ttsBusy=false}
        }
    }
    fun cancelNarration(){ttsJob?.cancel()}
    fun addManualCaption(){if(busy || totalDuration<=0)return;pushHistory();val start=playhead.coerceIn(0,(totalDuration-1).coerceAtLeast(0));val end=minOf(totalDuration,start+3000).coerceAtLeast(start+1);val cue=SrtCue(start,end,"NOVA LEGENDA");val cues=(lyrics?.cues.orEmpty()+cue).sortedBy {it.startMs};lyrics=SrtTrack(cues);lyricsName="Legendas manuais";persistCues();focus("Legenda",cues.indexOf(cue).toString())}
    fun addFx(preset:FxPreset){val clip=current ?: return;val start=timelineOffset;val end=start+speedMap(clip).outputDuration;if(end<=start)return;val layer=FxLayer(presetId=preset.id,start=start,end=end);updateStudio(studio.copy(fx=studio.fx+layer,recent=(listOf(preset.id)+studio.recent).distinct().take(30)),true);focus("FX",layer.id)}
    fun importFxPack(uri:Uri?){
        if(uri==null || busy)return
        importing=true
        viewModelScope.launch {
            val result=withContext(Dispatchers.IO){runCatching {
                val bytes=context.contentResolver.openInputStream(uri)?.use {input->
                    val out=java.io.ByteArrayOutputStream()
                    val buffer=ByteArray(8192)
                    while(true){
                        val n=input.read(buffer);if(n<0)break
                        require(out.size()+n<=1_000_000){"Pacote de efeitos muito grande (máximo 1 MB)."}
                        out.write(buffer,0,n)
                    }
                    out.toByteArray()
                } ?: error("Arquivo indisponível")
                FxPackParser.parse(bytes.toString(Charsets.UTF_8))
            }}
            importing=false
            result.onSuccess {presets->
                val ids=presets.map {it.id}.toSet()
                val merged=(studio.customFx.filterNot {it.id in ids}+presets).takeLast(200)
                updateStudio(studio.copy(customFx=merged),false)
                message="${presets.size} efeito(s) importado(s). Eles estão em Meus efeitos."
            }.onFailure {e->
                message="Não consegui importar esse efeito. Use .monstrofx ou um .prfpset do Premiere com efeitos compatíveis: ${e.localizedMessage}"
            }
        }
    }
    fun favorite(id:String){updateStudio(studio.copy(favorites=if(id in studio.favorites)studio.favorites-id else studio.favorites+id),false,false)}
    @Suppress("DEPRECATION")
    fun startVoiceover(){
        if(busy || voiceoverRecording || clips.isEmpty())return
        pauseAll()
        val dir=File(context.filesDir,"voiceover").apply {mkdirs()}
        val file=File(dir,"voice-${System.currentTimeMillis()}.m4a")
        val recorder=if(Build.VERSION.SDK_INT>=31)MediaRecorder(context) else MediaRecorder()
        try{
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare();recorder.start()
            voiceoverRecorder=recorder;voiceoverFile=file;voiceoverStart=playhead.coerceIn(0,(totalDuration-1).coerceAtLeast(0))
            voiceoverRecording=true;voiceoverStatus="Gravando dublagem…"
            seekTimeline(voiceoverStart);player.play()
        }catch(e:Exception){
            runCatching {recorder.release()};file.delete()
            voiceoverStatus="Não foi possível iniciar o microfone: ${e.localizedMessage}"
        }
    }
    fun stopVoiceover(){
        if(!voiceoverRecording)return
        val recorder=voiceoverRecorder;val file=voiceoverFile
        voiceoverRecorder=null;voiceoverFile=null;voiceoverRecording=false;pauseAll()
        val stopped=runCatching {recorder?.stop();recorder?.release();true}.getOrElse {runCatching {recorder?.release()};false}
        if(!stopped || file==null || !file.isFile || file.length()==0L){file?.delete();voiceoverStatus="Gravação muito curta ou interrompida.";return}
        val duration=runCatching {MediaMetadataRetriever().let {r->try{r.setDataSource(file.absolutePath);r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L}finally{r.release()}}}.getOrDefault(0L)
        if(duration<150){file.delete();voiceoverStatus="Gravação muito curta.";return}
        val available=(totalDuration-voiceoverStart).coerceAtLeast(1)
        val trimEnd=minOf(duration,available).coerceAtLeast(1)
        val layer=AudioLayer(uri=file.toURI().toString(),name="Dublagem",duration=duration,start=voiceoverStart,trimEnd=trimEnd)
        updateStudio(studio.copy(audio=studio.audio+layer),false)
        focus("Áudio",layer.id);voiceoverStatus="Dublagem adicionada · ${timeLabel(trimEnd)}"
    }
    fun autoBeatsAudio(id:String){
        val layer=studio.audio.find {it.id==id} ?: return
        if(busy)return
        pauseAll();beatBusy=true;beatProgress=0;beatStatus="Auto-Beats · analisando áudio…"
        beatJob=viewModelScope.launch {
            try{
                val times=withContext(Dispatchers.IO){BeatDetector(context).detect(layer.uri,layer.trimStart,layer.trimEnd){p->viewModelScope.launch {beatProgress=p;beatStatus="Auto-Beats · $p%"}}}
                beatBusy=false
                val start=layer.start;val end=layer.end
                val preserved=studio.markers.filterNot {it.label=="Beat" && it.time in start..end}
                val beats=times.map {TimelineMarker(time=(start+it).coerceAtMost(totalDuration),label="Beat")}
                    .filter {it.time<=totalDuration}
                updateStudio(studio.copy(markers=(preserved+beats).sortedBy {it.time}),false)
                beatStatus=if(beats.isEmpty())"Auto-Beats não encontrou picos claros." else "Auto-Beats · ${beats.size} marcadores criados."
            }catch(e:Exception){
                beatStatus=if(e is kotlinx.coroutines.CancellationException)"Auto-Beats cancelado" else "Auto-Beats falhou: ${e.localizedMessage}"
            }finally{beatBusy=false}
        }
    }
    fun cancelAutoBeats(){beatJob?.cancel()}
    fun importImage(uri:Uri?){if(uri!=null)importImages(listOf(uri))}
    fun importImages(uris:List<Uri>){
        if(uris.isEmpty() || busy || totalDuration<=0)return
        importing=true
        viewModelScope.launch {
            val result=withContext(Dispatchers.IO){runCatching {
                uris.take(20).map {uri->
                    val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0)else null} ?: "Imagem"
                    val dir=File(context.filesDir,"images").apply {mkdirs()}
                    val file=File(dir,"image-${UUID.randomUUID()}.png")
                    val bitmap=if(Build.VERSION.SDK_INT>=28){
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)){decoder,info,_->
                            val w=info.size.width;val h=info.size.height;val maxSide=maxOf(w,h)
                            if(maxSide>1440){val ratio=1440f/maxSide;decoder.setTargetSize((w*ratio).toInt().coerceAtLeast(1),(h*ratio).toInt().coerceAtLeast(1))}
                            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }else{
                        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                        context.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,bounds)}
                        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1440)sample*=2
                        val options=BitmapFactory.Options().apply {inSampleSize=sample}
                        context.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,options)} ?: error("Não foi possível abrir a imagem.")
                    }
                    file.outputStream().use {out->check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)){"Não foi possível salvar a imagem."}}
                    bitmap.recycle()
                    val start=playhead.coerceIn(0,(totalDuration-1).coerceAtLeast(0))
                    val end=minOf(totalDuration,start+3000).coerceAtLeast(start+1)
                    ImageLayer(path=file.absolutePath,name=name,start=start,end=end)
                }
            }}
            importing=false
            result.onSuccess {layers->
                if(layers.isNotEmpty()){
                    updateStudio(studio.copy(images=studio.images+layers),false)
                    focus("Camada",layers.last().id)
                }
            }.onFailure {message="Não foi possível importar a imagem: ${it.localizedMessage}"}
        }
    }
    fun importAudio(uri:Uri?){if(uri==null || busy)return;importing=true;viewModelScope.launch {
        val result=withContext(Dispatchers.IO){runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);val r=MediaMetadataRetriever();val duration=try{r.setDataSource(context,uri);r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()}finally{r.release()};require(duration>0);val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0)else null} ?: "Áudio";AudioLayer(uri=uri.toString(),name=name,duration=duration,start=playhead,trimEnd=if(totalDuration>playhead)minOf(duration,totalDuration-playhead)else duration)}}
        importing=false;result.onSuccess {updateStudio(studio.copy(audio=studio.audio+it),false);focus("Áudio",it.id)}.onFailure {message="Não foi possível importar o áudio: ${it.localizedMessage}"}
    }}
    fun updateCue(index:Int,text:String,start:Long,end:Long){if(busy)return;val old=lyrics ?: return;if(index !in old.cues.indices || text.isBlank() || start<0 || end<=start)return
        pushHistory();val cue=old.cues[index];val replacement=SrtCue(start,end,text,if(cue.text==text && cue.startMs==start && cue.endMs==end)cue.wordTimes else emptyList())
        val sorted=old.cues.mapIndexed {i,c->i to if(i==index)replacement else c}.sortedBy {it.second.startMs}
        lyrics=SrtTrack(sorted.map {it.second});focusedId=sorted.indexOfFirst {it.first==index}.toString()
        updateStudio(studio.copy(captionStyles=sorted.mapIndexedNotNull {newIndex,pair->studio.captionStyles[pair.first]?.let {newIndex to it}}.toMap()),false,false);persistCues()
    }
    private fun captionDocument(): JSONArray {
        val array=JSONArray()
        (lyrics?.cues ?: emptyList()).forEach {c->array.put(JSONObject().put("start",c.startMs).put("end",c.endMs).put("text",c.text).put("words",JSONArray().also {a->c.wordTimes.forEach {a.put(JSONArray().put(it.start).put(it.end))}}))}
        return array
    }
    private fun persistCues(){
        runCatching { writeUtf8TextAtomically(File(context.filesDir,"captions.json"), captionDocument().toString()) }
            .onFailure { message="Não foi possível salvar as legendas: ${it.localizedMessage}" }
        scheduleProjectSave()
    }
    private fun restoreCues(){val f=File(context.filesDir,"captions.json");if(!f.isFile)return;runCatching {val a=JSONArray(f.readText());lyrics=if(a.length()==0)null else SrtTrack((0 until a.length()).map {val c=a.getJSONObject(it);val w=c.optJSONArray("words");SrtCue(c.getLong("start"),c.getLong("end"),c.getString("text"),if(w==null)emptyList()else(0 until w.length()).map {j->val pair=w.getJSONArray(j);WordTime(pair.getLong(0),pair.getLong(1))})})}}
    fun installSpeech(){if(busy)return;speechBusy=true;speechStatus="Baixando português (31 MB)…";speechJob=viewModelScope.launch {
        try{withContext(Dispatchers.IO){AutoCaptions(context).install {percent->viewModelScope.launch {speechStatus="Preparando português: $percent%"}}};modelReady=true;speechStatus="Português pronto — reconhecimento no aparelho"}
        catch(e:Exception){speechStatus="Download interrompido. Tente novamente."}finally{speechBusy=false}
    }}
    fun autoCaption() {
        if(captionEngine=="gemini") recognizeGeminiCaptions(clips,studio,0)
        else recognizeCaptions(clips,studio,0)
    }
    fun autoCaptionAudio(id:String) {
        val layer=studio.audio.find {it.id==id} ?: return
        val end=if(totalDuration>layer.start)minOf(layer.trimEnd,layer.trimStart+totalDuration-layer.start)else layer.trimEnd
        val inputs=listOf(VideoClip(uri=layer.uri,name=layer.name,duration=layer.duration,trim=TrimRange(layer.trimStart,end)))
        if(captionEngine=="gemini") recognizeGeminiCaptions(inputs,StudioProject(),layer.start)
        else recognizeCaptions(inputs,StudioProject(),layer.start)
    }
    private fun applyCaptionResult(result:SrtTrack,offset:Long,name:String) {
        pushHistory()
        val track=if(offset==0L)result else SrtTrack(result.cues.map {cue->cue.copy(
            startMs=cue.startMs+offset,
            endMs=cue.endMs+offset,
            wordTimes=cue.wordTimes.map {WordTime(it.start+offset,it.end+offset)}
        )})
        lyrics=track
        studio=studio.copy(captionStyles=emptyMap())
        prefs.edit().putString("studio",StudioCodec.encode(studio)).apply()
        lyricsName=name
        persistCues()
        speechStatus="${track.cues.size} frases. Toque nas legendas para revisar."
        focus("Legenda")
    }
    private fun recognizeGeminiCaptions(inputs:List<VideoClip>,project:StudioProject,offset:Long) {
        if(busy || inputs.isEmpty())return
        if(!geminiReady){
            message="Gemini IA ainda não está conectado neste APK. O projeto precisa do app/google-services.json do Firebase. Você pode usar Offline agora."
            return
        }
        pauseAll();speechBusy=true;speechProgress=0;speechStatus="Gemini está preparando o áudio…"
        speechJob=viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO){
                    GeminiAudioCaptions(context).transcribe(inputs,project){percent->
                        viewModelScope.launch {
                            speechProgress=percent.coerceIn(0,100)
                            speechStatus="Gemini analisando o áudio: ${percent.coerceIn(0,100)}%"
                        }
                    }
                }
                applyCaptionResult(result,offset,"Legendas IA · Gemini")
            } catch(e:Exception) {
                if(e is kotlinx.coroutines.CancellationException){
                    speechStatus="Reconhecimento cancelado"
                } else {
                    runCatching {
                        val offline=AutoCaptions(context)
                        if(!offline.ready){
                            speechStatus="IA online indisponível · preparando reconhecimento offline (31 MB)…"
                            withContext(Dispatchers.IO){
                                offline.install {percent->viewModelScope.launch {
                                    speechProgress=percent.coerceIn(0,100)
                                    speechStatus="Preparando modo offline: ${percent.coerceIn(0,100)}%"
                                }}
                            }
                            modelReady=true
                        }
                        speechStatus="Continuando no aparelho, sem usar cota do Gemini…"
                        val fallback=withContext(Dispatchers.IO){
                            offline.transcribe(inputs,project){percent->
                                viewModelScope.launch {
                                    speechProgress=percent.coerceIn(0,100)
                                    speechStatus="Offline: reconhecendo fala ${percent.coerceIn(0,100)}%"
                                }
                            }
                        }
                        applyCaptionResult(fallback,offset,"Legendas automáticas · Offline")
                    }.onFailure {
                        speechStatus="Não consegui concluir a legenda agora. Verifique sua internet para preparar o modo offline e tente novamente."
                    }
                }
            } finally {speechBusy=false;speechProgress=null}
        }
    }
    private fun recognizeCaptions(inputs:List<VideoClip>,project:StudioProject,offset:Long) {
        if(busy || inputs.isEmpty())return
        if(!modelReady){message="Na aba Legenda, selecione Offline e toque em Baixar português (31 MB) primeiro.";return}
        pauseAll();speechBusy=true;speechProgress=0;speechStatus="Preparando áudio Offline…"
        speechJob=viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO){AutoCaptions(context).transcribe(inputs,project){percent->viewModelScope.launch {speechProgress=percent.coerceIn(0,100);speechStatus="Offline: reconhecendo fala ${percent.coerceIn(0,100)}%"}}}
                applyCaptionResult(result,offset,"Legendas automáticas · Offline")
            } catch(e:Exception) {speechStatus=if(e is kotlinx.coroutines.CancellationException)"Reconhecimento cancelado" else "Não foi possível legendar: ${e.localizedMessage}"}
            finally {speechBusy=false;speechProgress=null}
        }
    }
    fun cancelSpeech(){speechJob?.cancel()}
    fun translateCaptions(targetLanguage:String){
        val source=lyrics ?: run {message="Crie ou importe legendas antes de traduzir.";return}
        if(busy)return
        if(!GeminiCaptionTranslation(context).configured){message="Gemini IA não está conectado neste APK.";return}
        pauseAll();translationBusy=true;translationProgress=0;translationStatus="Traduzindo para $targetLanguage…"
        translationJob=viewModelScope.launch {
            try{
                val translated=withContext(Dispatchers.IO){
                    GeminiCaptionTranslation(context).translate(source,targetLanguage){p->
                        viewModelScope.launch {translationProgress=p;translationStatus="Traduzindo para $targetLanguage · $p%"}
                    }
                }
                pushHistory();lyrics=translated;lyricsName="Tradução IA · $targetLanguage";persistCues()
                translationProgress=100;translationStatus="Tradução pronta · $targetLanguage"
                message="Legendas traduzidas para $targetLanguage mantendo os mesmos tempos."
            }catch(e:Exception){
                translationStatus=if(e is kotlinx.coroutines.CancellationException)"Tradução cancelada" else GeminiSupport.userMessage(e)
            }finally{translationBusy=false}
        }
    }
    fun cancelTranslation(){translationJob?.cancel()}
    private fun studioPreviewEffects(clip:VideoClip):List<Effect>{
        val clock=FrameClock(preview={shaderPlayhead});val motion=studio.motions[clip.id] ?: ClipMotion();val zoom=motion.zoom
        val base=clip.copy(chaos=clip.chaos.copy(enabled=clip.chaos.enabled-ChaosFx.MOTION_BLUR.id,zoom=if(zoom.isEmpty())clip.chaos.zoom else 1f))
        val adjust=studio.adjustments[clip.id] ?: ClipAdjust()
        val hasTransform=zoom.isNotEmpty() || motion.rotation.isNotEmpty() || motion.x.isNotEmpty() || motion.y.isNotEmpty()
        return listOf(Presentation.createForHeight(480))+colorAdjustEffects(adjust)+previewEffects(base)+(if(!hasTransform)emptyList()else listOf(StudioEffect(null,zoom,clock,timelineOffset,motion.rotation,motion.x,motion.y)))+
            studio.fx.filter {it.end>timelineOffset && it.start<timelineOffset+speedMap(clip).outputDuration}.map {StudioEffect(it,emptyList(),clock,customPresets=studio.customFx)}+
            (if(clip.chaos.has(ChaosFx.MOTION_BLUR))listOf(ChaosEffect(ChaosSettings(setOf(ChaosFx.MOTION_BLUR.id))))else emptyList())+
            AspectBackgroundEffect(ExportFormat(canvasRatio,true,canvasBackground))
    }

    private fun createPlayer(): ExoPlayer {
        val instance = ExoPlayer.Builder(context).build()
        instance.setAudioAttributes(ClearCutAudioFocusPolicy.buildPreviewAttributes(), true)
        instance.setHandleAudioBecomingNoisy(true)
        instance.repeatMode = Player.REPEAT_MODE_OFF
        instance.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_ENDED && instance.playWhenReady && selected<clips.lastIndex && !busy){viewModelScope.launch {yield();if(player===instance){selected++;preview(0,true)}}}}
            override fun onPlayerError(error: PlaybackException) { handlePreviewError(instance, error) }
        })
        return instance
    }

    internal fun handlePreviewError(failedPlayer: ExoPlayer, error: PlaybackException) {
        viewModelScope.launch {
            yield()
            if (player !== failedPlayer) return@launch
            val detail = generateSequence(error as Throwable) { it.cause }.take(6)
                .joinToString(" → ") { it.message ?: it.javaClass.simpleName }
            message = "Falha na prévia: ${error.errorCodeName}. Seu projeto foi mantido.\n$detail"
        }
    }

    private var transformer: Transformer? = null
    private var renderingFile: File? = null
    private var polling: Job? = null

    init {
        runCatching {
            val data = JSONArray(prefs.getString("clips", "[]"))
            clips = (0 until data.length()).map { i ->
                val c = data.getJSONObject(i)
                VideoClip(c.getString("id"), c.getString("uri"), c.getString("name"),
                    c.getLong("duration"), TrimRange(c.getLong("start"), c.getLong("end")), c.getString("preset"),
                    ChaosSettings.restore(c.optJSONArray("fx")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                        c.optDouble("zoom", 1.0).toFloat()),
                    c.optDouble("volume",1.0).toFloat(),c.optBoolean("mirror",false))
            }
            mute = prefs.getBoolean("mute", false)
            safeMode = prefs.getBoolean("safeMode", true)
            output = prefs.getString("output", null)?.let { File(it) }?.takeIf { it.isFile && it.length() > 0 }
            vertical = canvasRatio=="9:16" || canvasRatio=="4:5"
            simpleLyrics = prefs.getBoolean("simpleLyrics", false)
            purpleLyrics = prefs.getBoolean("purpleLyrics", true)
            lyricsName = prefs.getString("lyricsName", "") ?: ""
            val subtitleFile = File(context.filesDir, "lyrics.srt")
            if (subtitleFile.isFile) runCatching { lyrics = SrtParser.parse(subtitleFile.readText()).track }
            restoreCues()
            val cleanedStudio=FxCatalog.sanitizeProject(studio)
            if(cleanedStudio!=studio){
                studio=cleanedStudio
                prefs.edit().putString("studio",StudioCodec.encode(studio)).apply()
            }
            preview()
        }.onFailure { clips = emptyList(); message = "Não foi possível restaurar o projeto. Importe os vídeos novamente." }
        prefs.registerOnSharedPreferenceChangeListener(projectPrefsListener)
        initializeProjectLibrary()
    }

    private fun captureProject(): JSONObject {
        val settings = JSONObject()
        prefs.all.filterKeys { it != "activeProjectId" }.forEach { (key, value) -> settings.put(key, value) }
        settings.put("studio", StudioCodec.encode(studio))
        return JSONObject().put("version",1).put("id",activeProjectId ?: UUID.randomUUID().toString())
            .put("name",projectName).put("updatedAt",System.currentTimeMillis())
            .put("settings",settings).put("captions",captionDocument())
    }

    private fun initializeProjectLibrary() {
        projectBusy=true
        val previous = captureProject()
        viewModelScope.launch {
            try {
                val loaded=withContext(Dispatchers.IO) {
                    val id=activeProjectId
                    if(id!=null) {
                        val saved=runCatching { projectStore.load(id) }.getOrNull()
                        val journal=runCatching {ProjectStore.validate(previous)}.getOrNull()
                        when {
                            saved!=null && journal!=null -> {
                                // SharedPreferences is the immediate journal;
                                // it can be newer than the debounced document.
                                journal.put("name",saved.optString("name","Projeto Monstro"))
                                projectStore.save(journal);journal
                            }
                            saved!=null -> saved
                            journal!=null -> projectStore.create("Projeto recuperado",journal.getJSONObject("settings"),journal.getJSONArray("captions"))
                            else -> error("O projeto e sua cópia não puderam ser recuperados.")
                        }
                    } else projectStore.create("Meu primeiro projeto",previous.getJSONObject("settings"),previous.getJSONArray("captions"))
                }
                val sameJournal=loaded.getJSONObject("settings").toString()==previous.getJSONObject("settings").toString() &&
                    loaded.getJSONArray("captions").toString()==previous.getJSONArray("captions").toString()
                if(sameJournal){
                    activeProjectId=loaded.getString("id");projectName=loaded.optString("name","Projeto Monstro")
                    prefs.edit().putString("activeProjectId",activeProjectId).apply()
                }else applyProject(loaded)
                refreshProjects()
            } catch(e:Exception) { message="Seu projeto atual foi mantido. Biblioteca: ${e.localizedMessage}" }
            finally { projectBusy=false }
        }
    }

    private fun scheduleProjectSave() {
        if(applyingProject || activeProjectId==null)return
        projectSaveJob?.cancel()
        projectSaveJob=viewModelScope.launch {
            delay(350)
            val snapshot=captureProject()
            try {
                withContext(Dispatchers.IO) { projectStore.save(snapshot) }
                refreshProjects()
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(e:Exception) { message="Não foi possível salvar o projeto: ${e.localizedMessage}. A versão anterior foi mantida." }
        }
    }

    private suspend fun refreshProjects() {
        val lists=withContext(Dispatchers.IO) { projectStore.list() to projectStore.list(true) }
        savedProjects=lists.first;trashedProjects=lists.second
    }

    private fun applyProject(document:JSONObject) {
        ProjectStore.validate(document)
        val settings=document.getJSONObject("settings")
        val data=JSONArray(settings.optString("clips","[]"))
        val nextClips=(0 until data.length()).map {i->
            val c=data.getJSONObject(i)
            VideoClip(c.getString("id"),c.getString("uri"),c.getString("name"),c.getLong("duration"),
                TrimRange(c.getLong("start"),c.getLong("end")),c.optString("preset","raw"),
                ChaosSettings.restore(c.optJSONArray("fx")?.let {a->(0 until a.length()).map {a.getString(it)}} ?: emptyList(),c.optDouble("zoom",1.0).toFloat()),
                c.optDouble("volume",1.0).toFloat(),c.optBoolean("mirror",false))
        }
        val nextStudio=FxCatalog.sanitizeProject(StudioCodec.decode(settings.optString("studio","{}")))
        val captions=document.optJSONArray("captions") ?: JSONArray()
        val nextLyrics=if(captions.length()==0)null else SrtTrack((0 until captions.length()).map {i->
            val c=captions.getJSONObject(i);val words=c.optJSONArray("words") ?: JSONArray()
            SrtCue(c.getLong("start"),c.getLong("end"),c.getString("text"),(0 until words.length()).map {j->
                val w=words.getJSONArray(j);WordTime(w.getLong(0),w.getLong(1))
            })
        })
        pauseAll();audioPlayers.values.forEach {it.release()};audioPlayers.clear()
        applyingProject=true
        try {
            val edit=prefs.edit().clear()
            settings.keys().forEach {key->when(val value=settings.get(key)) {
                is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value)
                is Int->edit.putInt(key,value);is Long->edit.putLong(key,value)
                is Number->edit.putFloat(key,value.toFloat())
            }}
            activeProjectId=document.getString("id");projectName=document.optString("name","Projeto Monstro")
            edit.putString("activeProjectId",activeProjectId).apply()
            clips=nextClips;selected=0;studio=nextStudio;lyrics=nextLyrics
            mute=settings.optBoolean("mute",false);safeMode=settings.optBoolean("safeMode",true)
            canvasRatio=settings.optString("canvasRatio","9:16");canvasBackground=settings.optString("canvasBackground","blur")
            bitrateMode=settings.optString("bitrateMode","recommended");exportCodec=settings.optString("exportCodec","H264")
            exportFps=settings.optInt("exportFps",30).takeIf {it in listOf(24,30,60)} ?: 30
            vertical=canvasRatio=="9:16" || canvasRatio=="4:5"
            simpleLyrics=settings.optBoolean("simpleLyrics",false);purpleLyrics=settings.optBoolean("purpleLyrics",true)
            lyricsName=settings.optString("lyricsName","");output=settings.optString("output","").takeIf {it.isNotBlank()}?.let {File(it)}?.takeIf {it.isFile && it.length()>0}
            undoStack.clear();redoStack.clear();historyVersion++;focusedId="";playhead=0
            writeUtf8TextAtomically(File(context.filesDir,"captions.json"),captions.toString())
            // The active document is authoritative. Stale SRT from a different
            // project must not return after an activity/process restart.
            File(context.filesDir,"lyrics.srt").delete()
            preview()
        } finally { applyingProject=false }
    }

    fun openProject(id:String) = projectAction { previous ->
        projectStore.save(previous);projectStore.load(id)
    }
    fun newProject(name:String="Novo projeto") = projectAction {previous->
        projectStore.save(previous);projectStore.create(name,JSONObject().put("clips","[]").put("studio","{}"),JSONArray())
    }
    fun duplicateProject(id:String) = projectAction {previous->projectStore.save(previous);projectStore.duplicate(id)}
    fun renameProject(id:String,name:String) = projectAction {previous->
        projectStore.save(previous);projectStore.rename(id,name);projectStore.load(previous.getString("id"))
    }
    fun trashProject(id:String,trashed:Boolean) = projectAction {previous->
        projectStore.save(previous);projectStore.trash(id,trashed)
        if(id==previous.getString("id") && trashed) {
            projectStore.list().firstOrNull()?.let {projectStore.load(it.id)}
                ?: projectStore.create("Novo projeto",JSONObject().put("clips","[]").put("studio","{}"),JSONArray())
        } else projectStore.load(previous.getString("id"))
    }

    private fun projectAction(action:(JSONObject)->JSONObject) {
        if(busy)return
        projectSaveJob?.cancel();pauseAll();projectBusy=true
        val previous=captureProject()
        viewModelScope.launch {
            try { val next=withContext(Dispatchers.IO){action(previous)};applyProject(next);refreshProjects() }
            catch(e:Exception){message="Não foi possível abrir o projeto: ${e.localizedMessage}. O projeto atual foi mantido."}
            finally{projectBusy=false}
        }
    }

    fun exportProjectDocument(uri:Uri?) {
        if(uri==null || busy)return
        projectSaveJob?.cancel();projectBusy=true
        val document=captureProject()
        viewModelScope.launch {
            try {withContext(Dispatchers.IO){projectStore.save(document);context.contentResolver.openOutputStream(uri,"wt")!!.use {it.write(document.toString().toByteArray(Charsets.UTF_8))}}
                message="Projeto salvo. O arquivo guarda a edição; mantenha as mídias originais acessíveis."
            }catch(e:Exception){message="Não foi possível salvar o projeto: ${e.localizedMessage}"}
            finally{projectBusy=false}
        }
    }
    fun importProjectDocument(uri:Uri?) {
        if(uri==null)return
        projectAction {previous->
            val imported=context.contentResolver.openInputStream(uri)!!.use {ProjectStore.validate(JSONObject(readUtf8WithByteLimit(it,ProjectStore.MAX_DOCUMENT_BYTES)))}
            // Decode before replacing any active data. Import always creates a
            // new identity, even when receiving one of our own backup files.
            StudioCodec.decode(imported.getJSONObject("settings").optString("studio","{}"))
            imported.getJSONObject("settings").remove("output")
            projectStore.save(previous)
            projectStore.create(imported.optString("name","Projeto importado"),imported.getJSONObject("settings"),imported.optJSONArray("captions") ?: JSONArray())
        }
    }

    private fun persist() {
        val data = JSONArray()
        clips.forEach { c -> data.put(JSONObject().put("id", c.id).put("uri", c.uri)
            .put("name", c.name).put("duration", c.duration).put("start", c.trim.start)
            .put("end", c.trim.end).put("preset", c.preset)
            .put("fx", JSONArray(c.chaos.enabled.toList())).put("zoom", c.chaos.zoom.toDouble())
            .put("volume",c.volume.toDouble()).put("mirror",c.mirror)) }
        prefs.edit().putString("clips", data.toString()).putBoolean("mute", mute).putBoolean("safeMode", safeMode).apply()
    }

    private fun preview(position: Long = 0, resume: Boolean = false) {
        // Recreate to fully detach a failed GPU pipeline and to return to direct decoding.
        shaderPlayhead=timelineOffset+(current?.let {speedMap(it).toOutput(position)} ?: 0);playhead=shaderPlayhead
        val oldPlayer = player
        player = createPlayer()
        oldPlayer.release()
        current?.let {
            val effects = studioPreviewEffects(it)
            if (effects.isNotEmpty()) player.setVideoEffects(effects)
            player.volume = if (mute) 0f else it.volume.coerceIn(0f,1f)
            player.setMediaItem(it.mediaItem(), position.coerceIn(0, it.trim.duration - 1))
            player.prepare()
            player.playWhenReady = resume
        }
    }

    fun select(index: Int) {
        if (busy || index !in clips.indices) return
        selected = index
        preview()
    }

    fun orderClipsByName() {
        if(busy || clips.size<2)return
        val selectedId=current?.id
        val ordered=orderMediaSequence(clips.map {MediaSequenceCandidate(it.id,it.name,null)},MediaSequenceOrder.NAME)
        val byId=clips.associateBy {it.id}
        pushHistory();clips=ordered.map {byId.getValue(it.key)}
        selected=clips.indexOfFirst {it.id==selectedId}.coerceAtLeast(0)
        persist();preview()
        message="Clipes ordenados pelo nome. Desfazer recupera a ordem anterior."
    }

    fun exportSubtitles(uri:Uri?,format:String) {
        if(uri==null || busy)return
        val track=lyrics ?: run{message="Crie ou importe legendas primeiro.";return}
        val text=when(format){"vtt"->SubtitleExport.vtt(track);"ass"->SubtitleExport.ass(track,studio.captionStyle);else->SubtitleExport.srt(track)}
        saving=true
        viewModelScope.launch {
            try {withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")!!.use {it.write(text.toByteArray(Charsets.UTF_8))}}
                message="Legendas ${format.uppercase()} salvas."
            }catch(e:Exception){message="Não foi possível salvar as legendas: ${e.localizedMessage}"}
            finally{saving=false}
        }
    }

    fun importVideos(uris: List<Uri>) {
        if (busy || uris.isEmpty()) return
        importing = true
        viewModelScope.launch {
            var failed = 0
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val retriever = MediaMetadataRetriever()
                        val duration = try {
                            retriever.setDataSource(context, uri)
                            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                                ?.takeIf { MediaDurationPolicy.isPlausible(it) } ?: error("Duração inválida ou maior que 24 horas")
                        } finally { retriever.release() }
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Vídeo"
                        VideoClip(uri = uri.toString(), name = name, duration = duration)
                    }.getOrElse { failed++; null }
                }
            }
            val firstNewIndex = clips.size
            if(added.isNotEmpty())pushHistory()
            clips = clips + added
            if (added.isNotEmpty()) { selected = firstNewIndex; persist(); preview() }
            importing = false
            if (failed > 0) message = "$failed arquivo(s) não puderam ser importados. Use vídeos locais acessíveis."
        }
    }

    fun toggleSafeMode() {
        if (busy) return
        safeMode = !safeMode
        persist()
    }

    fun toggleFx(fx: ChaosFx) {
        val clip = current ?: return
        edit(chaos = clip.chaos.toggle(fx))
    }

    fun edit(trim: TrimRange? = null, preset: String? = null, chaos: ChaosSettings? = null, volume:Float?=null) {
        if (busy) return
        val old = current ?: return
        if (trim != null && trim.end > old.duration) return
        val position = if (trim == null) player.currentPosition else 0L
        val playing = player.playWhenReady
        pushHistory();clips = clips.toMutableList().also { it[selected] = old.copy(trim = trim ?: old.trim, preset = preset ?: old.preset, chaos = chaos ?: old.chaos, volume=volume ?: old.volume) }
        persist(); preview(position, playing)
    }

    fun move(delta: Int) {
        val target = selected + delta
        if (busy || target !in clips.indices) return
        pushHistory();clips = clips.toMutableList().also { val c = it.removeAt(selected); it.add(target, c) }
        selected = target
        persist()
    }

    fun remove() {
        if (busy || current == null) return
        pushHistory();clips = clips.toMutableList().also { it.removeAt(selected) }
        selected = selected.coerceAtMost((clips.size - 1).coerceAtLeast(0))
        persist(); preview()
    }

    fun split() {
        if (busy) return
        val clip = current ?: return
        val ranges = clip.trim.split(player.currentPosition) ?: run {
            message = "Pause no ponto de corte, entre o início e o fim do clipe."; return
        }
        pushHistory();val rightId=UUID.randomUUID().toString()
        val motion=studio.motions[clip.id]
        var nextStudio=studio
        if(motion!=null){val sourceCut=player.currentPosition;val outputCut=speedMap(clip).toOutput(sourceCut)
            val left=ClipMotion(splitCurve(motion.speed,sourceCut,false),splitCurve(motion.zoom,outputCut,false),splitCurve(motion.rotation,outputCut,false),splitCurve(motion.x,outputCut,false),splitCurve(motion.y,outputCut,false))
            val right=ClipMotion(splitCurve(motion.speed,sourceCut,true),splitCurve(motion.zoom,outputCut,true),splitCurve(motion.rotation,outputCut,true),splitCurve(motion.x,outputCut,true),splitCurve(motion.y,outputCut,true))
            nextStudio=nextStudio.copy(motions=nextStudio.motions+(clip.id to left)+(rightId to right))
        }
        studio.adjustments[clip.id]?.let {nextStudio=nextStudio.copy(adjustments=nextStudio.adjustments+(rightId to it))}
        if(nextStudio!=studio){studio=nextStudio;prefs.edit().putString("studio",StudioCodec.encode(studio)).apply()}
        clips = clips.toMutableList().also {
            it[selected] = clip.copy(trim = ranges.first)
            it.add(selected + 1, clip.copy(id = rightId, trim = ranges.second))
        }
        persist(); preview()
    }

    fun toggleMute() {
        if (busy) return
        pushHistory();mute = !mute
        player.volume = if (mute) 0f else (current?.volume ?: 1f).coerceIn(0f,1f)
        persist()
    }
    fun setClipVolume(value:Float){
        val clip=current ?: return;if(busy)return
        edit(chaos=clip.chaos,volume=value.coerceIn(0f,2f))
    }
    fun toggleMirror(){
        val clip=current ?: return;if(busy)return
        val position=player.currentPosition;val playing=player.playWhenReady
        pushHistory();clips=clips.toMutableList().also {it[selected]=clip.copy(mirror=!clip.mirror)}
        persist();preview(position,playing)
    }
    fun extractCurrentAudio(){
        val clip=current ?: return;if(busy)return
        val layer=AudioLayer(uri=clip.uri,name="${clip.name} · áudio",duration=clip.duration,start=timelineOffset,trimStart=clip.trim.start,trimEnd=clip.trim.end,volume=clip.volume)
        updateStudio(studio.copy(audio=studio.audio+layer),false);focus("Áudio",layer.id)
    }

    fun selectCanvasRatio(ratio:String) {
        if(busy || ratio !in listOf("9:16","16:9","1:1","4:5"))return
        canvasRatio=ratio;vertical=ratio=="9:16" || ratio=="4:5"
        prefs.edit().putString("canvasRatio",ratio).putBoolean("vertical",vertical).apply()
        preview(player.currentPosition,player.playWhenReady)
    }
    fun selectCanvasBackground(mode:String) {
        if(busy || mode !in listOf("blur","solid","pattern"))return
        canvasBackground=mode;prefs.edit().putString("canvasBackground",mode).apply()
        preview(player.currentPosition,player.playWhenReady)
    }
    fun selectBitrateMode(mode:String) {
        if(busy || mode !in listOf("low","recommended","high"))return
        bitrateMode=mode;prefs.edit().putString("bitrateMode",mode).apply()
    }
    fun selectExportCodec(codec:String) {
        if(busy || codec !in listOf("H264","HEVC"))return
        if(codec=="HEVC" && !hevcSupported){message="Este aparelho não oferece encoder HEVC/H.265. Mantive H.264.";return}
        exportCodec=codec;prefs.edit().putString("exportCodec",codec).apply()
    }
    fun selectExportFps(fps:Int){
        if(busy || fps !in listOf(24,30,60))return
        exportFps=fps;prefs.edit().putInt("exportFps",fps).apply()
    }
    fun setExportFormat(isVertical: Boolean) { selectCanvasRatio(if(isVertical)"9:16" else "16:9") }
    fun toggleSimpleLyrics() {
        if (busy) return
        pushHistory();simpleLyrics = !simpleLyrics; prefs.edit().putBoolean("simpleLyrics", simpleLyrics).apply()
    }
    fun toggleLyricsColor() {
        if (busy) return
        pushHistory();purpleLyrics = !purpleLyrics; prefs.edit().putBoolean("purpleLyrics", purpleLyrics).apply()
    }
    fun removeLyrics() {
        if (busy) return
        pushHistory();lyrics = null; lyricsName = ""; persistCues(); File(context.filesDir,"lyrics.srt").delete()
        prefs.edit().remove("lyricsName").apply()
    }
    fun importLyrics(uri: Uri?) {
        if (uri == null || busy) return
        importing = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(2_000_000) } ?: error("Arquivo indisponível")
                val charset = when {
                    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
                    bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
                    else -> Charsets.UTF_8
                }
                val source = bytes.toString(charset)
                val parsed = SrtParser.parse(source)
                val destination = File(context.filesDir,"lyrics.srt")
                val temporary = File(context.filesDir,"lyrics.tmp")
                temporary.writeText(source)
                check(temporary.renameTo(destination)) { "Não foi possível salvar a legenda." }
                val name = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)
                    ?.use { if(it.moveToFirst()) it.getString(0) else null } ?: "Legenda SRT"
                Triple(parsed,name,source)
            } }
            importing = false
            result.onSuccess { (parsed,name,_) ->
                pushHistory();lyrics = parsed.track; updateStudio(studio.copy(captionStyles=emptyMap()),false,false); persistCues(); lyricsName = name; prefs.edit().putString("lyricsName",name).apply()
                message = "${parsed.track.cues.size} frases importadas." + if(parsed.skipped > 0) " ${parsed.skipped} blocos inválidos ignorados." else ""
            }.onFailure { message = "Não foi possível importar a legenda: ${it.localizedMessage}" }
        }
    }

    fun shareOutput(){
        val file=output?.takeIf {it.isFile && it.length()>0} ?: run {message="Exporte um MP4 primeiro.";return}
        runCatching {
            val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            val send=Intent(Intent.ACTION_SEND).apply {
                type="video/mp4"
                putExtra(Intent.EXTRA_STREAM,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData=android.content.ClipData.newRawUri("MONSTRO",uri)
            }
            context.startActivity(Intent.createChooser(send,"Compartilhar vídeo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {message="Não consegui abrir o compartilhamento: ${it.localizedMessage}"}
    }
    fun showMessage(text:String){message=text}
    fun clearMessage() { message = null }

    fun export() {
        if (busy || clips.isEmpty()) return
        try {
            ExportPreflight.checkStorage(totalDuration,exportFormat.bitrate,context.filesDir.usableSpace)
            val supported=android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any {info->
                info.isEncoder && info.supportedTypes.any {it.equals(exportFormat.videoMime,true)} &&
                    runCatching {info.getCapabilitiesForType(exportFormat.videoMime).videoCapabilities
                        .areSizeAndRateSupported(exportFormat.width,exportFormat.height,exportFormat.fps.toDouble())}.getOrDefault(false)
            }
            require(supported){"O aparelho não suporta este codec, resolução e FPS. Tente H.264, 30 FPS ou exportação leve."}
        }catch(e:Exception){message=e.localizedMessage;return}
        pauseAll()
        exporting = true
        progress = null
        val finalFile = File(context.filesDir, "monstro-${System.currentTimeMillis()}.mp4")
        val file = File(context.filesDir, ".${finalFile.name}.partial.mp4")
        val expectedDuration=totalDuration
        renderingFile = file
        try {
            val composition = studioComposition()
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder()
                    .setBitrate(exportFormat.bitrate).build())
                .setEnableFallback(true).build()
            val job = Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(exportFormat.videoMime).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        polling?.cancel()
                        transformer = null
                        viewModelScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    verifyExportedVideo(file,expectedDuration)
                                    syncFileData(file)
                                    moveFileReplacing(file,finalFile)
                                }
                                output=finalFile
                                prefs.edit().putString("output",finalFile.absolutePath).apply()
                                message="MP4 verificado e pronto! Toque em Salvar MP4."
                            }catch(e:Exception){file.delete();message="O MP4 não passou na verificação: ${e.localizedMessage}. O último vídeo válido foi mantido."}
                            finally{renderingFile=null;exporting=false;progress=null}
                        }
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        failExport("Falha na exportação: ${exportException.errorCodeName}. Tente clipes menores ou outro vídeo.")
                    }
                }).build()
            transformer = job
            job.start(composition, file.absolutePath)
            polling = viewModelScope.launch {
                val holder = ProgressHolder()
                while (exporting) {
                    progress = if (job.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null
                    delay(300)
                }
            }
        } catch (e: Exception) {
            failExport("Não foi possível exportar: ${e.localizedMessage ?: "erro de processamento"}")
        }
    }

    private fun failExport(reason: String) {
        transformer?.cancel(); transformer = null
        polling?.cancel()
        renderingFile?.delete(); renderingFile = null
        exporting = false; progress = null; message = reason
    }

    fun cancelExport() { if (exporting) failExport("Exportação cancelada. Seu projeto foi mantido.") }

    fun saveOutput(uri: Uri?) {
        if (uri == null || busy) return
        val file = output ?: return
        saving = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Destino indisponível")
                    stream.use { destination -> file.inputStream().use { it.copyTo(destination) } }
                }
            }
            saving = false
            message = if (result.isSuccess) "Vídeo salvo no local escolhido!" else "Não foi possível salvar. O MP4 continua disponível; tente outro local."
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(projectPrefsListener)
        projectSaveJob?.cancel()
        // SharedPreferences still provides the immediate working-state journal;
        // immutable project documents provide recovery and named versions.
        speechJob?.cancel();translationJob?.cancel();ttsJob?.cancel();beatJob?.cancel();aiEditJob?.cancel(); if(voiceoverRecording)runCatching {voiceoverRecorder?.stop()};runCatching {voiceoverRecorder?.release()}; audioPlayers.values.forEach {it.release()}; polling?.cancel(); transformer?.cancel(); renderingFile?.delete(); player.release()
        super.onCleared()
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = read(buffer); if (n < 0) break
        require(output.size()+n <= limit) { "SRT muito grande (máximo 2 MB)." }
        output.write(buffer,0,n)
    }
    return output.toByteArray()
}
