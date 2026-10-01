package com.monstro.v18.monstro

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.monstro.v18.engine.ProjectAutoSave
import com.monstro.v18.engine.db.ProjectDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class MonstroProjectMigration @Inject constructor(
    @ApplicationContext private val context:Context,
    private val dao:ProjectDao,
    private val autoSave:ProjectAutoSave,
) {
    private val lock=Mutex()
    suspend fun migrate()=lock.withLock {
        val progress=context.getSharedPreferences("monstro-migration-v18",0)
        val directory=File(context.filesDir,"projects")
        val sources=directory.listFiles().orEmpty().filter{it.extension in listOf("json","bak")}
            .map{it.nameWithoutExtension}.distinct()
        val errors=JSONArray()
        for(id in sources) {
            if(progress.getBoolean(id,false))continue
            runCatching {
                fun read(suffix:String):JSONObject {
                    val file=File(directory,"$id.$suffix")
                    require(file.length()<=4*1024*1024)
                    return JSONObject(file.readText())
                }
                val converted=runCatching{MonstroProjectConverter.convert(read("json"))}
                    .getOrElse{MonstroProjectConverter.convert(read("bak"))}
                check(autoSave.saveNow(converted)){"Autosave indisponível"}
                dao.insertProject(converted.project)
                check(progress.edit().putBoolean(id,true).commit())
            }.onFailure { errors.put(JSONObject().put("id",id).put("error",it.javaClass.simpleName)) }
        }
        if(sources.isEmpty() && !progress.getBoolean("journal",false)) {
            val prefs=context.getSharedPreferences("editor",0)
            if(prefs.contains("clips"))runCatching {
                val settings=JSONObject();prefs.all.forEach{(k,v)->if(v!=null && k!="activeProjectId")settings.put(k,v)}
                val captions=File(context.filesDir,"captions.json").takeIf{it.isFile && it.length()<4*1024*1024}?.readText()?.let(::JSONArray) ?: JSONArray()
                val converted=MonstroProjectConverter.convert(JSONObject().put("version",1).put("id","legacy-journal").put("name","Projeto Monstro recuperado").put("settings",settings).put("captions",captions))
                check(autoSave.saveNow(converted));dao.insertProject(converted.project)
                check(progress.edit().putBoolean("journal",true).commit())
            }.onFailure{errors.put(JSONObject().put("id","journal").put("error",it.javaClass.simpleName))}
        }
        // No document, preference or media from the original editor is removed.
        File(context.filesDir,"monstro-migration-report.json").writeText(JSONObject().put("failed",errors).put("originalsPreserved",true).toString())
    }
}
