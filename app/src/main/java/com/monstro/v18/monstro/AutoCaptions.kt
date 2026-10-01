package com.monstro.v18.monstro

import android.content.Context
import android.media.*
import android.net.Uri
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.URL
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlin.math.*

/** Recognizes decoded file audio, never the microphone. Audio never leaves the device. */
class AutoCaptions(private val context:Context) {
    val modelDirectory get()=File(context.filesDir,"speech/vosk-model-small-pt-0.3")
    private fun validModel(root:File)=(File(root,"am/final.mdl").isFile && File(root,"conf/model.conf").isFile) || (File(root,"final.mdl").isFile && File(root,"mfcc.conf").isFile)
    val ready get()=validModel(modelDirectory)
    suspend fun install(progress:(Int)->Unit) {
        if(ready)return
        val temporary=File(context.filesDir,"speech-download").apply {deleteRecursively();mkdirs()}
        val connection=URL("https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip").openConnection().apply {connectTimeout=30000;readTimeout=30000}
        try {
            var total=0L;val buffer=ByteArray(32768)
            connection.getInputStream().use { input->ZipInputStream(input).use { zip->
                while(true){currentCoroutineContext().ensureActive();val entry=zip.nextEntry ?: break
                    val file=File(temporary,entry.name)
                    require(file.canonicalPath.startsWith(temporary.canonicalPath+File.separator)){"Modelo inválido"}
                    if(entry.isDirectory)file.mkdirs() else {file.parentFile!!.mkdirs();file.outputStream().use { out->
                        while(true){currentCoroutineContext().ensureActive();val n=zip.read(buffer);if(n<0)break;total+=n;require(total<250_000_000){"Modelo excedeu o tamanho permitido"};out.write(buffer,0,n);progress((total/500_000).toInt().coerceAtMost(99))}
                    }}
                }
            }}
            require(validModel(File(temporary,"vosk-model-small-pt-0.3"))){"Download incompleto"}
            val destination=File(context.filesDir,"speech");destination.deleteRecursively();check(temporary.renameTo(destination));progress(100)
        } finally {temporary.deleteRecursively()}
    }
    suspend fun transcribe(clips:List<VideoClip>,project:StudioProject,progress:(Int)->Unit):SrtTrack {
        require(ready){"Baixe primeiro o modelo de português (31 MB)."}
        val cues=mutableListOf<SrtCue>();var offset=0L
        Model(modelDirectory.absolutePath).use { model->
            clips.forEachIndexed { index,clip->
                currentCoroutineContext().ensureActive()
                val speed=SpeedMap(clip.trim.duration,project.motions[clip.id]?.speed ?: emptyList())
                val words=decode(clip,model) { fraction->progress(((index+fraction)*100/clips.size).toInt()) }
                words.chunked(6).forEach { group->
                    val timed=group.map { (_,s,e)->WordTime(offset+speed.toOutput(s),offset+speed.toOutput(e)) }
                    if(timed.isNotEmpty()) cues+=SrtCue(timed.first().start,max(timed.first().start+1,timed.last().end),group.joinToString(" "){it.first},timed)
                }
                offset+=speed.outputDuration
            }
        }
        require(cues.isNotEmpty()) {"Não encontrei fala reconhecível. Tente áudio mais claro ou importe um SRT."}
        return SrtTrack(cues.sortedBy { it.startMs })
    }
    private suspend fun decode(clip:VideoClip,model:Model,onProgress:(Double)->Unit):List<Triple<String,Long,Long>> {
        val extractor=MediaExtractor();var decoder:MediaCodec?=null;var recognizer:Recognizer?=null
        val words=mutableListOf<Triple<String,Long,Long>>()
        fun collect(json:String){val results=JSONObject(json).optJSONArray("result") ?: return
            for(i in 0 until results.length()){val w=results.getJSONObject(i);val start=(w.getDouble("start")*1000).toLong().coerceIn(0,clip.trim.duration);val end=(w.getDouble("end")*1000).toLong().coerceIn(0,clip.trim.duration);if(end>start)words+=Triple(w.getString("word"),start,end)} }
        try {
            extractor.setDataSource(context,Uri.parse(clip.uri),null)
            val track=(0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true } ?: return emptyList()
            extractor.selectTrack(track);val format=extractor.getTrackFormat(track)
            extractor.seekTo(clip.trim.start*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!);decoder=codec
            codec.configure(format,null,null,0);codec.start()
            var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcm=AudioFormat.ENCODING_PCM_16BIT; var inDone=false;var outDone=false;val info=MediaCodec.BufferInfo()
            var lastActivity=System.currentTimeMillis()
            while(!outDone){currentCoroutineContext().ensureActive();check(System.currentTimeMillis()-lastActivity<30000){"O decodificador de áudio parou de responder"}
                if(!inDone){val i=codec.dequeueInputBuffer(10000);if(i>=0){val b=codec.getInputBuffer(i)!!;val time=extractor.sampleTime
                    val n=if(time<0 || time>=clip.trim.end*1000)-1 else extractor.readSampleData(b,0)
                    if(n<0){codec.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inDone=true}
                    else {codec.queueInputBuffer(i,0,n,time,0);extractor.advance()};lastActivity=System.currentTimeMillis()}}
                val o=codec.dequeueOutputBuffer(info,10000)
                if(o==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){val f=codec.outputFormat;rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);pcm=if(f.containsKey(MediaFormat.KEY_PCM_ENCODING))f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT}
                if(o>=0){lastActivity=System.currentTimeMillis();val b=codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN);b.position(info.offset);b.limit(info.offset+info.size)
                    require(pcm==AudioFormat.ENCODING_PCM_16BIT || pcm==AudioFormat.ENCODING_PCM_FLOAT){"Formato PCM não suportado"}
                    val bytes=if(pcm==AudioFormat.ENCODING_PCM_FLOAT)4 else 2;val count=b.remaining()/(bytes*channels);val mono=ShortArray(count);var kept=0
                    for(f in 0 until count){var sum=0f;repeat(channels){sum+=if(bytes==4)b.float*32767 else b.short.toFloat()}
                        val ms=info.presentationTimeUs/1000+f*1000L/rate
                        if(ms>=clip.trim.start && ms<clip.trim.end)mono[kept++]=(sum/channels).toInt().coerceIn(-32768,32767).toShort()}
                    if(kept>0){if(recognizer==null)recognizer=Recognizer(model,rate.toFloat()).apply {setWords(true)};if(recognizer!!.acceptWaveForm(mono,kept))collect(recognizer!!.result)}
                    onProgress(((info.presentationTimeUs/1000-clip.trim.start).toDouble()/clip.trim.duration).coerceIn(0.0,1.0))
                    outDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;codec.releaseOutputBuffer(o,false)
                }
            }
            recognizer?.let {collect(it.finalResult)}
        } finally {recognizer?.close();runCatching {decoder?.stop()};decoder?.release();extractor.release()}
        val clean=mutableListOf<Triple<String,Long,Long>>()
        words.sortedBy {it.second}.forEach {w->
            val last=clean.lastOrNull()
            val duplicate=last!=null && last.first.equals(w.first,true) && abs(last.second-w.second)<=80 && abs(last.third-w.third)<=80
            if(!duplicate)clean+=w
        }
        return clean
    }
}
