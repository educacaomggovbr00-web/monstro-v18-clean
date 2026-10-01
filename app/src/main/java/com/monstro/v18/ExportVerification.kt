package com.monstro.v18

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

/** ClearCut-style read-back: a completed Transformer callback alone is insufficient. */
internal fun verifyExportedVideo(file:File,expectedDurationMs:Long) {
    require(file.isFile && file.length()>0) {"Arquivo vazio."}
    val extractor=MediaExtractor()
    try {
        extractor.setDataSource(file.absolutePath)
        val track=(0 until extractor.trackCount).firstOrNull {i->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true
        } ?: error("Faixa de vídeo ausente.")
        val format=extractor.getTrackFormat(track)
        require(format.getInteger(MediaFormat.KEY_WIDTH)>0 && format.getInteger(MediaFormat.KEY_HEIGHT)>0)
        if(format.containsKey(MediaFormat.KEY_DURATION)) {
            val actualMs=format.getLong(MediaFormat.KEY_DURATION)/1000
            require(actualMs>0 && abs(actualMs-expectedDurationMs)<=max(1500L,expectedDurationMs/10)) {
                "Duração incompleta (${actualMs}ms de ${expectedDurationMs}ms)."
            }
        }
        extractor.selectTrack(track)
        val buffer=ByteBuffer.allocate(1024*1024)
        require(extractor.readSampleData(buffer,0)>0) {"Nenhum quadro de vídeo legível."}
    } finally {extractor.release()}
}
