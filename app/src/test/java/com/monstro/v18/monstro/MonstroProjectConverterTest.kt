package com.monstro.v18.monstro

import com.monstro.v18.engine.ProjectDocumentApplicator
import com.monstro.v18.engine.ProjectDocumentReadResult
import com.monstro.v18.model.EffectType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class MonstroProjectConverterTest {
    private fun old()=JSONObject().put("version",1).put("id","legacy-id").put("name","Minha edição").put("settings",JSONObject()
        .put("canvasRatio","9:16").put("clips",JSONArray().put(JSONObject().put("id","c").put("uri","content://local/1").put("name","Vídeo").put("duration",3000).put("start",100).put("end",2500).put("fx",JSONArray().put("glitch")).put("preset","trap")).toString())
        .put("studio",StudioCodec.encode(StudioProject(texts=listOf(TextLayer(text="MONSTRO")),markers=listOf(TimelineMarker(time=500,label="Beat")),fx=listOf(FxLayer(presetId="fx-2-0-6",start=100,end=2000))))))
        .put("captions",JSONArray().put(JSONObject().put("start",200).put("end",800).put("text","Olá mundo")))
    @Test fun sourceIsUnchangedAndFullDocumentRoundTrips() {
        val source=old();val before=source.toString();val converted=MonstroProjectConverter.convert(source)
        assertEquals(before,source.toString())
        assertEquals("Minha edição",converted.project.name)
        val clip=converted.state.tracks.single().clips.single()
        assertEquals(100L,clip.trimStartMs);assertEquals(2500L,clip.trimEndMs)
        assertTrue(clip.effects.any{it.type==EffectType.MONSTRO_CHAOS})
        assertTrue(clip.effects.any{it.type==EffectType.MONSTRO_COLOR})
        assertTrue(clip.effects.any{it.type==EffectType.MONSTRO_STUDIO})
        assertEquals("Olá mundo",clip.captions.single().text)
        assertEquals("MONSTRO",converted.state.textOverlays.single().text)
        assertEquals("Beat",converted.state.timelineMarkers.single().label)
        val read=ProjectDocumentApplicator.read(ProjectDocumentApplicator.encode(converted))
        assertTrue(read is ProjectDocumentReadResult.Loaded)
    }
    @Test fun migrationIdIsStableAndInvalidTrimIsRejected() {
        assertEquals(MonstroProjectConverter.convert(old()).project.id,MonstroProjectConverter.convert(old()).project.id)
        val bad=old();val settings=bad.getJSONObject("settings");val clips=JSONArray(settings.getString("clips"));clips.getJSONObject(0).put("end",4000);settings.put("clips",clips.toString())
        assertThrows(IllegalArgumentException::class.java){MonstroProjectConverter.convert(bad)}
    }
}
