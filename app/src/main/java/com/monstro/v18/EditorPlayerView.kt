package com.monstro.v18

import android.content.Context
import android.view.SurfaceView
import android.view.SurfaceHolder
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Supplies surface dimensions again after Media3 1.2.1 initializes its effects processor. */
@UnstableApi
class EditorPlayerView(context: Context) : PlayerView(context) {
    private var observed: ExoPlayer? = null
    private var listener: Player.Listener? = null

    fun bind(target: ExoPlayer, hasEffects: Boolean) {
        if (observed === target) return
        listener?.let { observed?.removeListener(it) }
        observed = target
        player = target
        var refreshed = false
        fun refreshSurface() {
            if (!hasEffects || refreshed || target.playbackState != Player.STATE_READY) return
            val view = videoSurfaceView as? SurfaceView ?: return
            if (!view.holder.surface.isValid) return
            refreshed = true
            // Before initialization, Media3 ignores the output-resolution message. Clearing
            // and rebinding supplies both the surface and its dimensions to the live processor.
            target.clearVideoSurfaceView(view)
            target.setVideoSurfaceView(view)
        }
        listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { refreshSurface() }
        }.also { target.addListener(it) }
        (videoSurfaceView as? SurfaceView)?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { refreshSurface() }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { refreshSurface() }
            override fun surfaceDestroyed(holder: SurfaceHolder) { }
        })
        refreshSurface()
    }
}
