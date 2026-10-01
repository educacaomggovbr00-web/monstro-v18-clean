package com.monstro.v18.clearcut

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SavedProject(val id: String, val name: String, val updatedAt: Long, val clips: Int, val trashed: Boolean)

/** Native Monstro documents using ClearCut's atomic-write and recovery boundary. */
class ProjectStore(private val directory: File) {
    companion object {
        const val MAX_DOCUMENT_BYTES = 4L * 1024 * 1024
        private val ID = Regex("[a-f0-9-]{36}")

        fun validate(document: JSONObject): JSONObject {
            require(document.getInt("version") == 1) { "Versão de projeto não suportada." }
            require(ID.matches(document.getString("id"))) { "Identificador de projeto inválido." }
            val prefs = document.getJSONObject("settings")
            val clips = JSONArray(prefs.optString("clips", "[]"))
            require(clips.length() <= 500) { "Projeto tem clipes demais." }
            for (i in 0 until clips.length()) {
                val clip = clips.getJSONObject(i)
                require(clip.getString("id").isNotBlank())
                clip.getString("name")
                val duration = clip.getLong("duration")
                require(MediaDurationPolicy.isPlausible(duration)) { "Duração de mídia inválida." }
                require(clip.getLong("start") in 0 until clip.getLong("end") && clip.getLong("end") <= duration)
                val uri = clip.getString("uri")
                require(uri.startsWith("content://") || uri.startsWith("file:")) { "Use mídias locais." }
            }
            val studio = JSONObject(prefs.optString("studio", "{}"))
            listOf("texts", "audio", "fx", "images", "markers").forEach { key ->
                require((studio.optJSONArray(key)?.length() ?: 0) <= 5000) { "Camadas demais no projeto." }
            }
            val captions=document.optJSONArray("captions") ?: JSONArray()
            require(captions.length() <= 20000)
            for(i in 0 until captions.length()) {
                val cue=captions.getJSONObject(i)
                val start=cue.getLong("start");val end=cue.getLong("end")
                require(start>=0 && end>start && MediaDurationPolicy.isPlausible(end)) { "Tempo de legenda inválido." }
                cue.getString("text")
                val words=cue.optJSONArray("words") ?: JSONArray()
                require(words.length()<=10000)
                for(j in 0 until words.length()) {
                    val word=words.getJSONArray(j)
                    require(word.length()==2 && word.getLong(0)>=start && word.getLong(1)>word.getLong(0) && word.getLong(1)<=end) { "Tempo por palavra inválido." }
                }
            }
            require(document.toString().toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES)
            return document
        }
    }

    private fun file(id: String, suffix: String = ".json"): File {
        require(ID.matches(id))
        return File(directory, id + suffix)
    }

    @Synchronized fun create(name: String, settings: JSONObject, captions: JSONArray): JSONObject {
        val document = JSONObject().put("version", 1).put("id", UUID.randomUUID().toString())
            .put("name", name.trim().take(80).ifBlank { "Projeto Monstro" })
            .put("updatedAt", System.currentTimeMillis()).put("settings", settings).put("captions", captions)
        save(document)
        return document
    }

    @Synchronized fun save(document: JSONObject) {
        validate(document)
        val id = document.getString("id")
        val target = file(id)
        val saved=if(target.isFile)runCatching {readFile(target)}.getOrNull() else null
        if(saved!=null && saved.optLong("updatedAt")>document.optLong("updatedAt"))return
        // Keep a separately valid generation. Never replace a good backup with
        // a damaged primary file after a crash or interrupted external copy.
        if (target.isFile) {
            runCatching { readFile(target) }.getOrNull()?.let {
                writeUtf8TextAtomically(file(id, ".bak"), it.toString())
            }
        }
        writeUtf8TextAtomically(target, document.toString())
    }

    private fun readFile(source: File): JSONObject {
        require(source.length() <= MAX_DOCUMENT_BYTES)
        return validate(JSONObject(source.inputStream().use { readUtf8WithByteLimit(it, MAX_DOCUMENT_BYTES) }))
    }

    @Synchronized fun load(id: String): JSONObject = runCatching { readFile(file(id)) }
        .getOrElse { readFile(file(id, ".bak")) }

    @Synchronized fun list(trashed: Boolean = false): List<SavedProject> {
        return directory.listFiles().orEmpty().filter { it.extension == "json" || it.extension == "bak" }
            .map { it.name.substringBeforeLast('.') }.distinct().mapNotNull { id ->
                runCatching { load(id) }.getOrNull()?.let { document ->
                    val deleted = document.optBoolean("trashed", false)
                    if (deleted != trashed) null else SavedProject(id, document.optString("name", "Projeto Monstro"),
                        document.optLong("updatedAt"), JSONArray(document.getJSONObject("settings").optString("clips", "[]")).length(), deleted)
                }
            }.sortedByDescending { it.updatedAt }
    }

    @Synchronized fun rename(id: String, name: String) {
        require(name.isNotBlank())
        save(load(id).put("name", name.trim().take(80)).put("updatedAt", System.currentTimeMillis()))
    }

    @Synchronized fun duplicate(id: String): JSONObject = load(id).let {
        create(it.optString("name") + " · cópia", it.getJSONObject("settings"), it.optJSONArray("captions") ?: JSONArray())
    }

    @Synchronized fun trash(id: String, trashed: Boolean) {
        save(load(id).put("trashed", trashed).put("updatedAt", System.currentTimeMillis()))
    }
}
