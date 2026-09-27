package com.monstro.v18

import android.app.Application
import androidx.media3.common.*
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.transformer.*
import com.google.common.collect.ImmutableList

@UnstableApi
fun EditorModel.studioComposition():Composition {
    var offset=0L
    val items=clips.flatMap {clip->
        val map=speedMap(clip);val clipOffset=offset
        val parts=map.slices.map {slice->
            val clock=FrameClock(clipOffset+slice.outputStart,slice.speed)
            val visual=mutableListOf<Effect>(FrameDropEffect.createDefaultFrameDropEffect(30f))
            visual+=previewEffects(clip)
            val zoom=studio.motions[clip.id]?.zoom ?: emptyList()
            if(zoom.isNotEmpty())visual+=StudioEffect(null,zoom,clock,clipOffset)
            visual+=studio.fx.filter {it.end>clipOffset+slice.outputStart && it.start<clipOffset+slice.outputStart+slice.outputDuration}.map {StudioEffect(it,emptyList(),clock)}
            visual+=AspectBackgroundEffect(exportFormat)
            if(lyrics!=null || studio.texts.isNotEmpty())visual+=OverlayEffect(ImmutableList.of<TextureOverlay>(StudioOverlay(studio,lyrics,simpleLyrics,clock)))
            if(slice.speed!=1f)visual+=SpeedChangeEffect(slice.speed)
            val sonic=SonicAudioProcessor().apply {setSpeed(slice.speed);setPitch(1f)}
            EditedMediaItem.Builder(clip.copy(trim=TrimRange(clip.trim.start+slice.sourceStart,clip.trim.start+slice.sourceEnd)).mediaItem())
                .setRemoveAudio(mute).setEffects(Effects(if(slice.speed==1f) emptyList() else listOf(sonic),visual)).build()
        }
        offset+=map.outputDuration;parts
    }
    val sequences=mutableListOf(EditedMediaItemSequence(items))
    val context=getApplication<Application>()
    studio.audio.filter {it.start<totalDuration}.forEach {layer->
        val audio=mutableListOf<EditedMediaItem>()
        if(layer.start>0)audio+=EditedMediaItem.Builder(MediaItem.fromUri(silenceFile(context.cacheDir,layer.start).toURI().toString())).setRemoveVideo(true).build()
        val end=minOf(layer.trimEnd,layer.trimStart+totalDuration-layer.start)
        val item=MediaItem.Builder().setUri(layer.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(layer.trimStart).setEndPositionMs(end).build()).build()
        audio+=EditedMediaItem.Builder(item).setRemoveVideo(true).setEffects(Effects(listOf(VolumeProcessor(layer.volume)),emptyList())).build()
        sequences+=EditedMediaItemSequence(audio)
    }
    return Composition.Builder(sequences).experimentalSetForceAudioTrack(!mute || studio.audio.isNotEmpty())
        .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
}
