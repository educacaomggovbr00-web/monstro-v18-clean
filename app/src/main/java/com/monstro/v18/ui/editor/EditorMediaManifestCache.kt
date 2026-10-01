package com.monstro.v18.ui.editor

import com.monstro.v18.model.Clip
import com.monstro.v18.model.ImageOverlay
import com.monstro.v18.model.Track
import com.monstro.v18.model.TrackType

private const val MEDIA_MANIFEST_KEY_SEPARATOR = '\u001F'

internal fun mediaManifestCacheKey(
    tracks: List<Track>,
    imageOverlays: List<ImageOverlay>
): String {
    val references = linkedMapOf<String, String>()
    tracks.forEach { track ->
        val mediaType = when (track.type) {
            TrackType.AUDIO -> "audio"
            else -> "video"
        }
        track.clips.forEach { clip ->
            collectClipMediaManifestKeys(
                clip = clip,
                mediaType = mediaType,
                references = references
            )
        }
    }
    imageOverlays.forEach { overlay ->
        references.putIfAbsent(overlay.sourceUri.toString(), "image")
    }
    return references.entries.joinToString(separator = MEDIA_MANIFEST_KEY_SEPARATOR.toString()) { (uri, type) ->
        "$type:$uri"
    }
}

private fun collectClipMediaManifestKeys(
    clip: Clip,
    mediaType: String,
    references: LinkedHashMap<String, String>
) {
    references.putIfAbsent(clip.sourceUri.toString(), mediaType)
    clip.compoundClips.forEach { child ->
        collectClipMediaManifestKeys(
            clip = child,
            mediaType = mediaType,
            references = references
        )
    }
}
