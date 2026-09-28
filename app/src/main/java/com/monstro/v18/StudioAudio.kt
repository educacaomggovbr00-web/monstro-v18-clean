package com.monstro.v18
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File

@UnstableApi
class VolumeProcessor(private val volume:Float):BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat {
        if(inputAudioFormat.encoding!=C.ENCODING_PCM_16BIT)throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer:ByteBuffer){val out=replaceOutputBuffer(inputBuffer.remaining());while(inputBuffer.remaining()>=2)out.putShort((inputBuffer.short*volume).toInt().coerceIn(-32768,32767).toShort());out.flip()}
}
fun silenceFile(directory:File,milliseconds:Long):File {
    val file=File(directory,"silence-$milliseconds.wav");if(file.isFile)return file
    val size=(milliseconds*32).coerceAtLeast(32).toInt()
    val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {put("RIFF".toByteArray());putInt(size+36);put("WAVEfmt ".toByteArray());putInt(16);putShort(1);putShort(1);putInt(16000);putInt(32000);putShort(2);putShort(16);put("data".toByteArray());putInt(size)}
    file.outputStream().use {out->out.write(header.array());val zeros=ByteArray(8192);var left=size;while(left>0){val n=minOf(left,zeros.size);out.write(zeros,0,n);left-=n}}
    return file
}

/** Canonical stereo PCM keeps Media3 1.2's mixer inputs compatible. */
@UnstableApi
class StereoProcessor:BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat {
        if(inputAudioFormat.encoding!=C.ENCODING_PCM_16BIT)throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate,2,C.ENCODING_PCM_16BIT)
    }
    override fun queueInput(inputBuffer:ByteBuffer){
        val channels=inputAudioFormat.channelCount;val frames=inputBuffer.remaining()/(2*channels)
        val out=replaceOutputBuffer(frames*4)
        repeat(frames){val left=inputBuffer.short;val right=if(channels>1)inputBuffer.short else left;repeat((channels-2).coerceAtLeast(0)){inputBuffer.short};out.putShort(left);out.putShort(right)}
        out.flip()
    }
}
@UnstableApi
@UnstableApi
class EnvelopeVolumeProcessor(
    private val volume:Float,
    private val fadeInMs:Long,
    private val fadeOutMs:Long,
    private val durationMs:Long
):BaseAudioProcessor(){
    private var frames=0L
    private var rate=44100
    private var channels=2
    override fun onConfigure(inputAudioFormat:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat{
        if(inputAudioFormat.encoding!=C.ENCODING_PCM_16BIT)throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        rate=inputAudioFormat.sampleRate;channels=inputAudioFormat.channelCount
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer:ByteBuffer){
        val count=inputBuffer.remaining()/(2*channels)
        val out=replaceOutputBuffer(inputBuffer.remaining())
        repeat(count){
            val timeMs=frames*1000L/rate
            val inGain=if(fadeInMs<=0)1f else (timeMs.toFloat()/fadeInMs).coerceIn(0f,1f)
            val outGain=if(fadeOutMs<=0 || durationMs<=0)1f else ((durationMs-timeMs).toFloat()/fadeOutMs).coerceIn(0f,1f)
            val gain=(volume*minOf(inGain,outGain)).coerceIn(0f,2f)
            repeat(channels){out.putShort((inputBuffer.short*gain).toInt().coerceIn(-32768,32767).toShort())}
            frames++
        }
        out.flip()
    }
    override fun onFlush(){frames=0L}
    override fun onReset(){frames=0L}
}
@UnstableApi
fun canonicalAudio(
    speed:Float=1f,
    volume:Float=1f,
    pitch:Float=1f,
    fadeInMs:Long=0,
    fadeOutMs:Long=0,
    durationMs:Long=0
):List<AudioProcessor> = listOf(
    androidx.media3.common.audio.SonicAudioProcessor().apply {setSpeed(speed);setPitch(pitch.coerceIn(.5f,2f));setOutputSampleRateHz(44100)},
    StereoProcessor(),EnvelopeVolumeProcessor(volume,fadeInMs,fadeOutMs,durationMs)
)
