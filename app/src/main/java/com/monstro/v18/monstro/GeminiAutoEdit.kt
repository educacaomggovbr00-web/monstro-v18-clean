package com.monstro.v18.monstro

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.max

data class AiClipLook(
    val clipIndex:Int,
    val preset:String,
    val brightness:Float,
    val contrast:Float,
    val saturation:Float,
    val temperature:Float
)
data class AiMotionKey(
    val clipIndex:Int,
    val timeMs:Long,
    val zoom:Float,
    val x:Float,
    val y:Float,
    val rotation:Float
)
data class AiSpeedKey(val clipIndex:Int,val timeMs:Long,val speed:Float)
data class AiFxSuggestion(
    val startMs:Long,val endMs:Long,val category:String,val intensity:Float,val variant:Int
)
data class AiMarker(val timeMs:Long,val label:String)
data class AiEditPlan(
    val summary:String,
    val ratio:String,
    val looks:List<AiClipLook>,
    val motions:List<AiMotionKey>,
    val speeds:List<AiSpeedKey>,
    val effects:List<AiFxSuggestion>,
    val markers:List<AiMarker>
)

/**
 * Gemini-powered creative edit planner.
 * It samples a small number of frames instead of uploading the whole source video,
 * then combines visual context with existing captions and beat markers.
 */
class GeminiAutoEdit(private val context:Context){
    val configured get()=FirebaseApp.getApps(context).isNotEmpty()
    private data class Sample(val timeMs:Long,val clipIndex:Int,val bitmap:Bitmap)

    suspend fun analyze(
        clips:List<VideoClip>,
        project:StudioProject,
        captions:SrtTrack?,
        requestedStyle:String,
        progress:(Int)->Unit,
        currentRatio:String="9:16"
    ):AiEditPlan{
        require(configured){"Firebase AI Logic não está conectado."}
        require(clips.isNotEmpty()){"Importe pelo menos um vídeo."}
        progress(3)
        val profile=VideoPerceptionAnalyzer.analyze(clips,project,captions,currentRatio)
        val samples=sampleFrames(clips,project,profile.frameBudget){progress((3+it*27/100).coerceAtMost(30))}
        require(samples.isNotEmpty()){"Não consegui extrair quadros para a análise de IA."}
        try{
            val model=Firebase.ai(backend=GenerativeBackend.googleAI()).generativeModel(
                modelName=GeminiSupport.GENERAL_MODEL,
                generationConfig=generationConfig {
                    responseMimeType="application/json"
                    maxOutputTokens=4096
                }
            )
            val total=profile.durationMs
            val transcript=captions?.cues?.take(80)?.joinToString("\n"){cue->
                "[${cue.startMs}-${cue.endMs}ms] ${cue.text}"
            }?.take(9000).orEmpty()
            val beats=project.markers.filter {it.label.equals("Beat",true)}.take(160).joinToString(","){it.time.toString()}
            val clipInfo=buildString {
                var offset=0L
                clips.forEachIndexed {index,clip->
                    val map=SpeedMap(clip.trim.duration,project.motions[clip.id]?.speed ?: emptyList())
                    append("clip=$index nome=${clip.name} timeline=${offset}..${offset+map.outputDuration}ms duracao=${map.outputDuration}ms\n")
                    offset+=map.outputDuration
                }
            }

            progress(35)
            val prompt=content {
                samples.forEach {sample->
                    text("FRAME clip=${sample.clipIndex} timeline_ms=${sample.timeMs}")
                    image(sample.bitmap)
                }
                text(
                    """
                    Você é o editor automático do app Monstro V18. Analise os frames, o contexto temporal,
                    a fala e os beats. Crie uma edição coerente com o conteúdo; não jogue efeitos aleatórios.

                    Estilo pedido pelo usuário: $requestedStyle
                    Percepção automática local: ${profile.label}
                    Vídeo curto: ${profile.shortForm}
                    Duração total: $total ms
                    Clipes:
                    $clipInfo

                    Transcrição existente:
                    ${if(transcript.isBlank())"(sem transcrição)" else transcript}

                    Beats detectados (timeline ms):
                    ${if(beats.isBlank())"(sem beats)" else beats}

                    Recursos reais disponíveis no app:
                    - presets de cor: raw, neon, trap, dark, cinema
                    - FX curados por categoria: Glitch, RGB, Shake, Trap, Motion, Retro, VHS, Cinematic, Light, Blur
                    - keyframes de zoom, posição X/Y e rotação
                    - speed curve de 0.25x até 4x
                    - proporções: 9:16, 16:9, 1:1, 4:5

                    Retorne SOMENTE JSON válido:
                    {
                      "summary":"resumo curto do conceito",
                      "ratio":"9:16",
                      "looks":[
                        {"clip_index":0,"preset":"cinema","brightness":0.0,"contrast":0.1,"saturation":8.0,"temperature":0.05}
                      ],
                      "motions":[
                        {"clip_index":0,"time_ms":0,"zoom":1.0,"x":0.0,"y":0.0,"rotation":0.0}
                      ],
                      "speeds":[
                        {"clip_index":0,"time_ms":0,"speed":1.0}
                      ],
                      "effects":[
                        {"start_ms":500,"end_ms":900,"category":"Shake","intensity":0.8,"variant":4}
                      ],
                      "markers":[
                        {"time_ms":1000,"label":"Impacto"}
                      ]
                    }

                    Regras:
                    - Use poucos efeitos e somente quando fizerem sentido com cena, fala, movimento ou beat.
                    - intensity entre 0.15 e 1.35.
                    - variant entre 0 e 49.
                    - motion x/y entre -0.35 e 0.35; zoom entre 0.75 e 2.2; rotation entre -20 e 20 graus.
                    - speed entre 0.25 e 4.0.
                    - time_ms de motions/speeds é relativo ao clipe indicado.
                    - start_ms/end_ms de effects e time_ms de markers são relativos à timeline completa.
                    - Preserve legibilidade de legendas; evite excesso de shake/glitch quando houver fala.
                    - Se o conteúdo for conversa/vlog, prefira cortes visuais limpos, zoom discreto e cor natural.
                    - Se for música/trap, sincronize impactos com beats e use FX de forma rítmica.
                    - Se for gameplay/ação, destaque picos de movimento.
                    - Máximo: 3 motions por clipe, 4 speed keys por clipe, ${profile.effectBudget} efeitos e 16 marcadores.
                    - Em vídeo de fala/conversa, prefira zero efeitos ou no máximo efeitos muito discretos.
                    - Em vídeo curto, preserve ritmo e clareza; não empilhe efeitos só para preencher a edição.
                    """.trimIndent()
                )
            }
            progress(50)
            val raw=model.generateContent(prompt).text ?: error("Gemini não retornou um plano de edição.")
            progress(88)
            return parse(raw,clips,total,profile.effectBudget,profile.recommendedRatio).also {progress(100)}
        } finally {
            samples.forEach {if(!it.bitmap.isRecycled)it.bitmap.recycle()}
        }
    }

    private suspend fun sampleFrames(
        clips:List<VideoClip>,
        project:StudioProject,
        maxFrames:Int,
        progress:(Int)->Unit
    ):List<Sample>{
        val result=mutableListOf<Sample>()
        val perClip=max(1,maxFrames/clips.size.coerceAtMost(maxFrames))
        var timelineOffset=0L
        clips.forEachIndexed {index,clip->
            currentCoroutineContext().ensureActive()
            val map=SpeedMap(clip.trim.duration,project.motions[clip.id]?.speed ?: emptyList())
            val count=minOf(perClip,maxFrames-result.size)
            if(count<=0)return@forEachIndexed
            val retriever=MediaMetadataRetriever()
            try{
                retriever.setDataSource(context,Uri.parse(clip.uri))
                repeat(count){n->
                    currentCoroutineContext().ensureActive()
                    val fraction=(n+1f)/(count+1f)
                    val local=(clip.trim.duration*fraction).toLong().coerceIn(0,(clip.trim.duration-1).coerceAtLeast(0))
                    val source=clip.trim.start+local
                    val raw=if(Build.VERSION.SDK_INT>=27)
                        retriever.getScaledFrameAtTime(source*1000,MediaMetadataRetriever.OPTION_CLOSEST,384,216)
                    else retriever.getFrameAtTime(source*1000,MediaMetadataRetriever.OPTION_CLOSEST)
                    raw?.let {frame->
                        val bitmap=if(frame.width>512 || frame.height>512){
                            val scale=minOf(512f/frame.width,512f/frame.height)
                            Bitmap.createScaledBitmap(frame,(frame.width*scale).toInt().coerceAtLeast(1),(frame.height*scale).toInt().coerceAtLeast(1),true)
                                .also {if(it!==frame)frame.recycle()}
                        }else frame
                        result+=Sample(timelineOffset+map.toOutput(local),index,bitmap)
                    }
                    progress(((index*count+n+1)*100/(clips.size*count).coerceAtLeast(1)).coerceIn(0,100))
                    if(result.size>=maxFrames)return@repeat
                }
            } finally {retriever.release()}
            timelineOffset+=map.outputDuration
            if(result.size>=maxFrames)return@forEachIndexed
        }
        return result
    }

    private fun parse(raw:String,clips:List<VideoClip>,total:Long,effectBudget:Int,fallbackRatio:String):AiEditPlan{
        val clean=raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root=JSONTokener(clean).nextValue() as? JSONObject ?: error("Plano de edição inválido.")
        fun JSONArray?.objects()=if(this==null)emptyList() else (0 until length()).mapNotNull {optJSONObject(it)}
        val looks=root.optJSONArray("looks").objects().mapNotNull {o->
            val i=o.optInt("clip_index",-1);if(i !in clips.indices)null else AiClipLook(
                i,o.optString("preset","raw").takeIf {it in setOf("raw","neon","trap","dark","cinema")} ?: "raw",
                o.optDouble("brightness",0.0).toFloat().coerceIn(-.5f,.5f),
                o.optDouble("contrast",0.0).toFloat().coerceIn(-.6f,.6f),
                o.optDouble("saturation",0.0).toFloat().coerceIn(-60f,60f),
                o.optDouble("temperature",0.0).toFloat().coerceIn(-.6f,.6f)
            )
        }
        val motions=root.optJSONArray("motions").objects().mapNotNull {o->
            val i=o.optInt("clip_index",-1);if(i !in clips.indices)null else AiMotionKey(
                i,o.optLong("time_ms",0).coerceIn(0,clips[i].trim.duration),
                o.optDouble("zoom",1.0).toFloat().coerceIn(.75f,2.2f),
                o.optDouble("x",0.0).toFloat().coerceIn(-.35f,.35f),
                o.optDouble("y",0.0).toFloat().coerceIn(-.35f,.35f),
                o.optDouble("rotation",0.0).toFloat().coerceIn(-20f,20f)
            )
        }.groupBy {it.clipIndex}.flatMap {(_,v)->v.sortedBy {it.timeMs}.take(3)}
        val speeds=root.optJSONArray("speeds").objects().mapNotNull {o->
            val i=o.optInt("clip_index",-1);if(i !in clips.indices)null else AiSpeedKey(
                i,o.optLong("time_ms",0).coerceIn(0,clips[i].trim.duration),
                o.optDouble("speed",1.0).toFloat().coerceIn(.25f,4f)
            )
        }.groupBy {it.clipIndex}.flatMap {(_,v)->v.sortedBy {it.timeMs}.take(4)}
        val validCategories=setOf("Glitch","RGB","Shake","Trap","Motion","Retro","VHS","Cinematic","Light","Blur")
        val effects=root.optJSONArray("effects").objects().mapNotNull {o->
            val start=o.optLong("start_ms",0).coerceIn(0,total)
            val end=o.optLong("end_ms",start+300).coerceIn(start,total)
            val category=o.optString("category","Cinematic")
            if(end<=start || category !in validCategories)null else AiFxSuggestion(
                start,end,category,o.optDouble("intensity",.7).toFloat().coerceIn(.15f,1.35f),o.optInt("variant",0).coerceIn(0,49)
            )
        }.take(effectBudget)
        val markers=root.optJSONArray("markers").objects().map {o->
            AiMarker(o.optLong("time_ms",0).coerceIn(0,total),o.optString("label","IA").take(28))
        }.take(16)
        val ratio=root.optString("ratio",fallbackRatio).takeIf {it in setOf("9:16","16:9","1:1","4:5")} ?: fallbackRatio
        return AiEditPlan(root.optString("summary","Edição automática pronta."),ratio,looks,motions,speeds,effects,markers)
    }
}
