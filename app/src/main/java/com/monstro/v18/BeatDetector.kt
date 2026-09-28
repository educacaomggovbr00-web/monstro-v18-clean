package com.monstro.v18

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight local beat detector. Decodes audio to PCM, builds a 50 ms energy
 * envelope, and selects locally prominent peaks with a minimum spacing.
 */
class BeatDetector(private val context:Context){
    suspend fun detect(uri:String,startMs:Long,endMs:Long,progress:(Int)->Unit):List<Long>{
        require(endMs>startMs){"Trecho de áudio inválido."}
        val extractor=MediaExtractor();var decoder:MediaCodec?=null
        val sums=mutableMapOf<Int,Double>();val counts=mutableMapOf<Int,Int>()
        try{
            extractor.setDataSource(context,Uri.parse(uri),null)
            val track=(0 until extractor.trackCount).firstOrNull {i->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true
            } ?: error("Esta mídia não possui faixa de áudio.")
            extractor.selectTrack(track)
            val format=extractor.getTrackFormat(track)
            extractor.seekTo(startMs*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!);decoder=codec
            codec.configure(format,null,null,0);codec.start()
            var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcm=AudioFormat.ENCODING_PCM_16BIT
            var inDone=false;var outDone=false;val info=MediaCodec.BufferInfo()
            while(!outDone){
                currentCoroutineContext().ensureActive()
                if(!inDone){
                    val input=codec.dequeueInputBuffer(10000)
                    if(input>=0){
                        val buffer=codec.getInputBuffer(input)!!
                        val t=extractor.sampleTime
                        val n=if(t<0 || t>=endMs*1000)-1 else extractor.readSampleData(buffer,0)
                        if(n<0){codec.queueInputBuffer(input,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inDone=true}
                        else {codec.queueInputBuffer(input,0,n,t,0);extractor.advance()}
                    }
                }
                val output=codec.dequeueOutputBuffer(info,10000)
                if(output==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    val f=codec.outputFormat
                    rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    pcm=if(f.containsKey(MediaFormat.KEY_PCM_ENCODING))f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                }
                if(output>=0){
                    val b=codec.getOutputBuffer(output)!!.order(ByteOrder.LITTLE_ENDIAN)
                    b.position(info.offset);b.limit(info.offset+info.size)
                    val bytes=if(pcm==AudioFormat.ENCODING_PCM_FLOAT)4 else 2
                    require(pcm==AudioFormat.ENCODING_PCM_16BIT || pcm==AudioFormat.ENCODING_PCM_FLOAT){"Formato PCM não suportado."}
                    val frames=b.remaining()/(bytes*channels)
                    repeat(frames){frame->
                        var sum=0f
                        repeat(channels){sum+=if(bytes==4)b.float else b.short/32768f}
                        val mono=sum/channels
                        val ms=info.presentationTimeUs/1000 + frame*1000L/rate
                        if(ms in startMs until endMs){
                            val bucket=((ms-startMs)/50L).toInt()
                            sums[bucket]=(sums[bucket] ?: 0.0)+abs(mono).toDouble()
                            counts[bucket]=(counts[bucket] ?: 0)+1
                        }
                    }
                    progress((((info.presentationTimeUs/1000-startMs).toDouble()/(endMs-startMs))*100).toInt().coerceIn(0,99))
                    outDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0
                    codec.releaseOutputBuffer(output,false)
                }
            }
        } finally {
            runCatching {decoder?.stop()};decoder?.release();extractor.release()
        }

        val bucketCount=((endMs-startMs+49)/50).toInt().coerceAtLeast(1)
        val energy=DoubleArray(bucketCount){i->(sums[i] ?: 0.0)/(counts[i] ?: 1)}
        val beats=mutableListOf<Long>();var last=-1000L
        for(i in 2 until energy.size-2){
            val from=max(0,i-6);val to=minOf(energy.lastIndex,i+6)
            var local=0.0;var n=0
            for(j in from..to)if(j!=i){local+=energy[j];n++}
            local/=n.coerceAtLeast(1)
            val peak=energy[i]>=energy[i-1] && energy[i]>=energy[i+1]
            val strong=energy[i]>.012 && energy[i]>local*1.32
            val time=i*50L
            if(peak && strong && time-last>=220){beats+=time;last=time;if(beats.size>=400)break}
        }
        if(beats.size<2){
            val avg=energy.average()
            for(i in 1 until energy.size-1){
                val time=i*50L
                if(energy[i]>avg*1.18 && energy[i]>=energy[i-1] && energy[i]>=energy[i+1] && time-last>=300){
                    beats+=time;last=time;if(beats.size>=200)break
                }
            }
        }
        progress(100)
        return beats.distinct().sorted()
    }
}
