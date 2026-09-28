package com.monstro.v18

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
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
    var vertical by mutableStateOf(canvasRatio=="9:16" || canvasRatio=="4:5"); private set
    var lyrics by mutableStateOf<SrtTrack?>(null); private set
    var lyricsName by mutableStateOf(""); private set
    var simpleLyrics by mutableStateOf(false); private set
    var purpleLyrics by mutableStateOf(true); private set
    val exportFormat get() = ExportFormat(canvasRatio,safeMode,canvasBackground,bitrateMode,exportCodec)
    val hevcSupported get() = runCatching { android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { info->info.isEncoder && info.supportedTypes.any { it.equals(MimeTypes.VIDEO_H265,true) } } }.getOrDefault(false)
    val timelineOffset get() = clips.take(selected).sumOf { speedMap(it).outputDuration }

    var importing by mutableStateOf(false); private set
    var exporting by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var progress by mutableStateOf<Int?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var output by mutableStateOf<File?>(null); private set
    val busy get() = importing || exporting || saving || speechBusy
    val current get() = clips.getOrNull(selected)
    var compatibilityPreview by mutableStateOf(prefs.getBoolean("compatibilityPreview", false)); private set
    var player by mutableStateOf(createPlayer()); private set

    var studio by mutableStateOf(runCatching { StudioCodec.decode(prefs.getString("studio","{}")!!) }.getOrDefault(StudioProject())); private set
    var inspector by mutableStateOf("Vídeo"); private set
    var focusedId by mutableStateOf(""); private set
    var playhead by mutableStateOf(0L); private set
    @Volatile private var shaderPlayhead=0L
    var speechStatus by mutableStateOf(""); private set
    var speechProgress by mutableStateOf<Int?>(null); private set
    var speechBusy by mutableStateOf(false); private set
    var modelReady by mutableStateOf(AutoCaptions(context).ready); private set
    var captionEngine by mutableStateOf(prefs.getString("captionEngine","gemini") ?: "gemini"); private set
    val geminiReady get()=GeminiAudioCaptions(context).configured
    fun selectCaptionEngine(engine:String){if(engine !in listOf("gemini","offline") || busy)return;captionEngine=engine;prefs.edit().putString("captionEngine",engine).apply()}
    private var speechJob:Job?=null
    private val audioPlayers=mutableMapOf<String,ExoPlayer>()
    fun speedMap(clip:VideoClip)=SpeedMap(clip.trim.duration,studio.motions[clip.id]?.speed ?: emptyList())
    val totalDuration get()=clips.sumOf { speedMap(it).outputDuration }
    fun focus(kind:String,id:String=""){inspector=kind;focusedId=id}
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
            p.volume=layer.volume.coerceIn(0f,1f)
            if(active){val target=layer.trimStart+playhead-layer.start;if(kotlin.math.abs(p.currentPosition-target)>150)p.seekTo(target);p.playWhenReady=player.isPlaying && !busy}else p.pause()
        }
    }
    fun pauseAll(){player.pause();audioPlayers.values.forEach {it.pause()}}
    fun setMotion(motion:ClipMotion){val clip=current ?: return;updateStudio(studio.copy(motions=studio.motions+(clip.id to motion)))}
    fun addText(){val start=if(playhead>=totalDuration)(totalDuration-3000).coerceAtLeast(0)else playhead;val end=minOf(totalDuration,start+3000);if(end<=start)return;val layer=TextLayer(start=start,end=end);updateStudio(studio.copy(texts=studio.texts+layer),false);focus("Texto",layer.id)}
    fun addManualCaption(){if(busy || totalDuration<=0)return;pushHistory();val start=playhead.coerceIn(0,(totalDuration-1).coerceAtLeast(0));val end=minOf(totalDuration,start+3000).coerceAtLeast(start+1);val cue=SrtCue(start,end,"NOVA LEGENDA");val cues=(lyrics?.cues.orEmpty()+cue).sortedBy {it.startMs};lyrics=SrtTrack(cues);lyricsName="Legendas manuais";persistCues();focus("Legenda",cues.indexOf(cue).toString())}
    fun addFx(preset:FxPreset){val clip=current ?: return;val start=timelineOffset;val end=start+speedMap(clip).outputDuration;if(end<=start)return;val layer=FxLayer(presetId=preset.id,start=start,end=end);if(compatibilityPreview){compatibilityPreview=false;prefs.edit().putBoolean("compatibilityPreview",false).apply()};updateStudio(studio.copy(fx=studio.fx+layer,recent=(listOf(preset.id)+studio.recent).distinct().take(30)),true);focus("FX",layer.id)}
    fun favorite(id:String){updateStudio(studio.copy(favorites=if(id in studio.favorites)studio.favorites-id else studio.favorites+id),false,false)}
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
    private fun persistCues(){val cues=lyrics?.cues ?: emptyList();val array=JSONArray();cues.forEach {c->array.put(JSONObject().put("start",c.startMs).put("end",c.endMs).put("text",c.text).put("words",JSONArray().also {a->c.wordTimes.forEach {a.put(JSONArray().put(it.start).put(it.end))}}))};File(context.filesDir,"captions.json").writeText(array.toString())}
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
                } else if(modelReady) {
                    speechStatus="Gemini indisponível. Tentando reconhecimento Offline…"
                    runCatching {
                        val fallback=withContext(Dispatchers.IO){
                            AutoCaptions(context).transcribe(inputs,project){percent->
                                viewModelScope.launch {
                                    speechProgress=percent.coerceIn(0,100)
                                    speechStatus="Offline: reconhecendo fala ${percent.coerceIn(0,100)}%"
                                }
                            }
                        }
                        applyCaptionResult(fallback,offset,"Legendas automáticas · Offline")
                    }.onFailure {fallbackError->
                        speechStatus="Não foi possível legendar: ${fallbackError.localizedMessage}"
                    }
                } else {
                    speechStatus="Gemini falhou: ${e.localizedMessage}. Baixe o modo Offline para ter fallback."
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
    private fun studioPreviewEffects(clip:VideoClip):List<Effect>{
        val clock=FrameClock(preview={shaderPlayhead});val zoom=studio.motions[clip.id]?.zoom ?: emptyList()
        val base=clip.copy(chaos=clip.chaos.copy(enabled=clip.chaos.enabled-ChaosFx.MOTION_BLUR.id,zoom=if(zoom.isEmpty())clip.chaos.zoom else 1f))
        val adjust=studio.adjustments[clip.id] ?: ClipAdjust()
        return listOf(Presentation.createForHeight(480))+colorAdjustEffects(adjust)+previewEffects(base)+(if(zoom.isEmpty())emptyList()else listOf(StudioEffect(null,zoom,clock,timelineOffset)))+
            studio.fx.filter {it.end>timelineOffset && it.start<timelineOffset+speedMap(clip).outputDuration}.map {StudioEffect(it,emptyList(),clock)}+
            (if(clip.chaos.has(ChaosFx.MOTION_BLUR))listOf(ChaosEffect(ChaosSettings(setOf(ChaosFx.MOTION_BLUR.id))))else emptyList())+
            AspectBackgroundEffect(ExportFormat(canvasRatio,true,canvasBackground))
    }

    private fun createPlayer(): ExoPlayer {
        val instance = ExoPlayer.Builder(context).build()
        instance.repeatMode = Player.REPEAT_MODE_OFF
        instance.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_ENDED && instance.playWhenReady && selected<clips.lastIndex && !busy){viewModelScope.launch {yield();if(player===instance){selected++;preview(0,true)}}}}
            override fun onPlayerError(error: PlaybackException) { handlePreviewError(instance, error) }
        })
        return instance
    }

    internal fun handlePreviewError(failedPlayer: ExoPlayer, error: PlaybackException) {
        viewModelScope.launch {
            // Leave the listener callback before releasing the renderer that reported the error.
            yield()
            if (player !== failedPlayer) return@launch
            val processingFailure = error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED
            if (processingFailure && !compatibilityPreview) {
                val position = player.currentPosition
                val resume = player.playWhenReady
                compatibilityPreview = true
                prefs.edit().putBoolean("compatibilityPreview", true).apply()
                preview(position, resume)
                message = "Ativei a prévia de compatibilidade. Seus cortes e efeitos foram mantidos. " +
                    "A prévia mostra o vídeo sem efeitos; a exportação continua aplicando os efeitos escolhidos."
            } else {
                val detail = generateSequence(error as Throwable) { it.cause }.take(6)
                    .joinToString(" → ") { it.message ?: it.javaClass.simpleName }
                message = "Falha na prévia: ${error.errorCodeName}. Seu projeto foi mantido.\n$detail"
            }
        }
    }

    fun toggleCompatibilityPreview() {
        if (busy) return
        val position = player.currentPosition
        val resume = player.playWhenReady
        compatibilityPreview = !compatibilityPreview
        prefs.edit().putBoolean("compatibilityPreview", compatibilityPreview).apply()
        preview(position, resume)
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
            restoreCues(); preview()
        }.onFailure { clips = emptyList(); message = "Não foi possível restaurar o projeto. Importe os vídeos novamente." }
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
            val effects = if (compatibilityPreview) emptyList() else studioPreviewEffects(it)
            // Even an empty setVideoEffects call can initialize the frame processor.
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
                                ?.takeIf { it > 0 } ?: error("Duração inválida")
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
            val left=ClipMotion(splitCurve(motion.speed,sourceCut,false),splitCurve(motion.zoom,outputCut,false))
            val right=ClipMotion(splitCurve(motion.speed,sourceCut,true),splitCurve(motion.zoom,outputCut,true))
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

    fun clearMessage() { message = null }

    fun export() {
        if (busy || clips.isEmpty()) return
        pauseAll()
        exporting = true
        progress = null
        val file = File(context.filesDir, "monstro-${System.currentTimeMillis()}.mp4")
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
                        renderingFile = null
                        exporting = false
                        if (!file.isFile || file.length() == 0L) {
                            file.delete(); message = "A exportação terminou sem gerar um arquivo válido."; return
                        }
                        val previous = output
                        output = file
                        prefs.edit().putString("output", file.absolutePath).apply()
                        previous?.takeIf { it != file }?.delete()
                        message = "MP4 pronto! Toque em Salvar MP4 para escolher onde guardar."
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
        speechJob?.cancel(); audioPlayers.values.forEach {it.release()}; polling?.cancel(); transformer?.cancel(); renderingFile?.delete(); player.release()
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
