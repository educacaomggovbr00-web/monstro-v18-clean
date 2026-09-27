package com.monstro.v18

import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PreviewTest {
    @Test fun previewRawEffectsAndRecoveryWithoutLosingEdits() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val input = File(context.cacheDir, "preview.mp4")
        instrumentation.context.assets.open("sample.mp4").use { source -> input.outputStream().use { source.copyTo(it) } }
        val prefs = context.getSharedPreferences("editor", 0)
        val clip = JSONObject().put("id", "preview").put("uri", input.toURI().toString())
            .put("name", "preview").put("duration", 2000).put("start", 100).put("end", 1900).put("preset", "raw")
        prefs.edit().clear().putString("clips", JSONArray().put(clip).toString()).commit()
        val allFx = ChaosSettings(ChaosFx.values().map { it.id }.toSet(), 1.4f)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            fun play(edit: (EditorModel) -> Unit) {
                val ended = CountDownLatch(1)
                val rendered = CountDownLatch(1)
                val failure = AtomicReference<Throwable?>()
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[EditorModel::class.java]
                    edit(model)
                    model.player.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { rendered.countDown() }
                        override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) ended.countDown() }
                        override fun onPlayerError(error: PlaybackException) { failure.set(error); ended.countDown(); rendered.countDown() }
                    })
                    model.player.play()
                }
                assertTrue("Preview never finished", ended.await(30, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("Preview failed", it) }
                assertTrue("Preview never rendered to PlayerView", rendered.await(5, TimeUnit.SECONDS))
            }
            play { it.select(0) }
            play { it.edit(preset = "cinema", chaos = allFx) }
            play { it.edit(preset = "raw", chaos = ChaosSettings()) }

            // Exercise recovery with the exact reported error and retain all saved edits.
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[EditorModel::class.java]
                model.edit(preset = "cinema", chaos = allFx)
                model.player.seekTo(400)
                model.handlePreviewError(model.player, PlaybackException("Injected GPU failure", null,
                    PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED))
            }
            instrumentation.waitForIdleSync()
            play { model ->
                assertTrue(model.compatibilityPreview)
                assertEquals(allFx, model.current!!.chaos)
                assertEquals("cinema", model.current!!.preset)
                assertEquals(TrimRange(100, 1900), model.current!!.trim)
                assertTrue("Playback position was lost", model.player.currentPosition >= 400)
                model.clearMessage()
                model.select(0)
            }
        } finally {
            scenario.close()
            prefs.edit().clear().commit()
            input.delete()
        }
    }
}
