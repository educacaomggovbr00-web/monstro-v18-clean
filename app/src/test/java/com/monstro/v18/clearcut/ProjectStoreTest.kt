package com.monstro.v18.clearcut

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectStoreTest {
    private fun withStore(block:(ProjectStore,File)->Unit) {
        val dir=Files.createTempDirectory("monstro-projects").toFile()
        try{block(ProjectStore(dir),dir)}finally{dir.deleteRecursively()}
    }
    private fun settings()=JSONObject().put("clips",JSONArray().put(JSONObject()
        .put("id","clip-1").put("uri","content://local/video/1").put("name","video.mp4")
        .put("duration",3000).put("start",100).put("end",2500)).toString())
        .put("studio","{\"texts\":[{\"id\":\"t\",\"text\":\"Meu edit\"}]}")

    @Test fun namedProjectsDuplicateAndRestoreWithoutChangingOriginal()=withStore {store,_->
        val original=store.create("Meu projeto",settings(),JSONArray())
        val copy=store.duplicate(original.getString("id"))
        assertNotEquals(original.getString("id"),copy.getString("id"))
        assertEquals(original.getJSONObject("settings").toString(),copy.getJSONObject("settings").toString())
        store.trash(copy.getString("id"),true)
        assertEquals(1,store.list().size);assertEquals(1,store.list(true).size)
        store.trash(copy.getString("id"),false)
        assertEquals(2,store.list().size)
        store.rename(copy.getString("id"),"Nome novo")
        assertEquals("Meu projeto",store.load(original.getString("id")).getString("name"))
    }
    @Test fun corruptedLatestGenerationRecoversPreviousDocument()=withStore {store,dir->
        val document=store.create("Salvo",settings(),JSONArray())
        val id=document.getString("id")
        store.rename(id,"Alterado")
        File(dir,"$id.json").writeText("{truncated")
        assertEquals("Salvo",store.load(id).getString("name"))
        assertEquals(1,store.list().size)
        store.save(store.load(id).put("updatedAt",System.currentTimeMillis()+1000))
        assertEquals("Salvo",store.load(id).getString("name"))
    }
    @Test fun staleAsyncSaveCannotOverwriteNewerProject()=withStore {store,_->
        val old=store.create("Original",settings(),JSONArray())
        val newer=JSONObject(old.toString()).put("name","Novo").put("updatedAt",old.getLong("updatedAt")+1000)
        store.save(newer);store.save(old)
        assertEquals("Novo",store.load(old.getString("id")).getString("name"))
    }
    @Test fun untrustedFutureAndOverflowDocumentsAreRejectedBeforeSave()=withStore {store,dir->
        val saved=store.create("Bom",settings(),JSONArray())
        val future=JSONObject(saved.toString()).put("version",999)
        assertThrows(IllegalArgumentException::class.java){store.save(future)}
        assertEquals("Bom",store.load(saved.getString("id")).getString("name"))
        val invalid=JSONObject(saved.toString())
        val clips=JSONArray(invalid.getJSONObject("settings").getString("clips"))
        clips.getJSONObject(0).put("duration",Long.MAX_VALUE)
        invalid.getJSONObject("settings").put("clips",clips.toString())
        assertThrows(IllegalArgumentException::class.java){store.save(invalid)}
        assertEquals(1,dir.listFiles()!!.count{it.extension=="json"})
    }
}
