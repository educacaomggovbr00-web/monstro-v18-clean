package com.monstro.v18

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@UnstableApi
@RunWith(AndroidJUnit4::class)
class ProjectIntegrationTest {
    @Test fun editsSurviveProjectSwitchDuplicateAndActivityRecreation() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java))
        fun model(activity:MainActivity)=ViewModelProvider(activity)[EditorModel::class.java]
        fun idle() {
            val ready=AtomicBoolean(false)
            val deadline=System.currentTimeMillis()+10000
            while(System.currentTimeMillis()<deadline) {
                scenario.onActivity {ready.set(!model(it).projectBusy)}
                if(ready.get())return
                Thread.sleep(50)
            }
            fail("Project operation timed out")
        }
        var first="";var copy=""
        try {
            idle()
            scenario.onActivity {model(it).newProject("Projeto de regressão")};idle()
            scenario.onActivity {
                val m=model(it)
                first=m.savedProjects.first{p->p.name=="Projeto de regressão"}.id
                m.updateStudio(m.studio.copy(texts=listOf(TextLayer(text="Edição preservada")),markers=listOf(TimelineMarker(time=0,label="Início"))),false)
                m.duplicateProject(first)
            };idle()
            scenario.onActivity {
                val m=model(it)
                copy=m.savedProjects.first{p->p.name=="Projeto de regressão · cópia"}.id
                assertNotEquals(first,copy)
                assertEquals("Edição preservada",m.studio.texts.single().text)
                m.newProject("Projeto vazio")
            };idle()
            scenario.onActivity {val m=model(it);assertTrue(m.studio.texts.isEmpty());m.openProject(first)};idle()
            scenario.onActivity {assertEquals("Edição preservada",model(it).studio.texts.single().text)}
            scenario.recreate();idle()
            scenario.onActivity {
                val m=model(it)
                assertEquals("Projeto de regressão",m.projectName)
                assertEquals("Início",m.studio.markers.single().label)
                m.trashProject(copy,true)
            };idle()
            scenario.onActivity {val m=model(it);assertTrue(m.trashedProjects.any{p->p.id==copy});m.trashProject(copy,false)};idle()
            scenario.onActivity {assertTrue(model(it).savedProjects.any{p->p.id==copy})}
        } finally { scenario.close() }
    }
}
