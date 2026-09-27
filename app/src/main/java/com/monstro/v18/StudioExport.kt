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
    var offset=0L;var sourceOffset=0L
    val items=clips.flatMap {clip->
        val map=speedMap(clip);val clipOffset=offset
        val parts=map.slices.map {slice->
            val clock=FrameClock(clipOffset+slice.outputStart,slice.speed,sourceStart=sourceOffset+slice.sourceStart)
            val visual=mutableListOf<Effect>()
            val zoom=studio.motions[clip.id]?.zoom ?: emptyList()
            visual+=previewEffects(clip.copy(chaos=clip.chaos.copy(enabled=clip.chaos.enabled-ChaosFx.MOTION_BLUR.id,zoom=if(zoom.isEmpty())clip.chaos.zoom else 1f)))
            if(zoom.isNotEmpty())visual+=StudioEffect(null,zoom,clock,clipOffset)
            visual+=studio.fx.filter {it.end>clipOffset+slice.outputStart && it.start<clipOffset+slice.outputStart+slice.outputDuration}.map {StudioEffect(it,emptyList(),clock)}
            if(clip.chaos.has(ChaosFx.MOTION_BLUR))visual+=ChaosEffect(ChaosSettings(setOf(ChaosFx.MOTION_BLUR.id)))
            visual+=AspectBackgroundEffect(exportFormat)
            if(lyrics!=null || studio.texts.isNotEmpty())visual+=OverlayEffect(ImmutableList.of<TextureOverlay>(StudioOverlay(studio,lyrics,simpleLyrics,clock)))
            visual+=TimelineSpeedEffect(slice.speed,clipOffset+slice.outputStart,sourceOffset+slice.sourceStart)
            visual+=FrameDropEffect.createDefaultFrameDropEffect(30f)
            
            EditedMediaItem.Builder(clip.copy(trim=TrimRange(clip.trim.start+slice.sourceStart,clip.trim.start+slice.sourceEnd)).mediaItem())
                .setRemoveAudio(mute).setEffects(Effects(canonicalAudio(slice.speed),visual)).build()
        }
        offset+=map.outputDuration;sourceOffset+=clip.trim.duration;parts
    }
    val sequences=mutableListOf(EditedMediaItemSequence(items))
    val context=getApplication<Application>()
    studio.audio.filter {it.start<totalDuration}.forEach {layer->
        val audio=mutableListOf<EditedMediaItem>()
        if(layer.start>0)audio+=EditedMediaItem.Builder(MediaItem.fromUri(silenceFile(context.cacheDir,layer.start).toURI().toString())).setRemoveVideo(true).setEffects(Effects(canonicalAudio(),emptyList())).build()
        val end=minOf(layer.trimEnd,layer.trimStart+totalDuration-layer.start)
        val item=MediaItem.Builder().setUri(layer.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(layer.trimStart).setEndPositionMs(end).build()).build()
        audio+=EditedMediaItem.Builder(item).setRemoveVideo(true).setEffects(Effects(canonicalAudio(volume=layer.volume),emptyList())).build()
        sequences+=EditedMediaItemSequence(audio)
    }
    return Composition.Builder(sequences).experimentalSetForceAudioTrack(!mute || studio.audio.isNotEmpty())
        .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
}
