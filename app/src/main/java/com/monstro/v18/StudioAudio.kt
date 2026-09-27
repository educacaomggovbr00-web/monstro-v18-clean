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
