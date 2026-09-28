package com.monstro.v18

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest

/**
 * Portable Monstro FX pack.
 *
 * A .monstrofx file is JSON and only selects/render recipes that the app already
 * knows how to draw. It never executes code from a downloaded file.
 */
object FxPackParser {
    private const val MAX_EFFECTS = 200

    fun parse(source:String):List<FxPreset> {
        require(source.length <= 1_000_000) { "Pacote de efeitos muito grande (máximo 1 MB)." }
        val root=JSONTokener(source).nextValue()
        val items=when(root){
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("effects") ?: JSONArray().put(root)
            else -> error("Formato de pacote inválido.")
        }
        require(items.length() in 1..MAX_EFFECTS) { "O pacote precisa ter entre 1 e $MAX_EFFECTS efeitos." }

        val result=mutableListOf<FxPreset>()
        for(i in 0 until items.length()){
            val o=items.optJSONObject(i) ?: continue
            val name=o.optString("name").trim().take(80)
            require(name.isNotBlank()) { "Efeito ${i+1} está sem nome." }
            val engine=o.optInt("engine",-1)
            val recipe=o.optInt("recipe",-1)
            val envelope=o.optInt("envelope",-1)
            require(engine in 0..19) { "$name: engine precisa estar entre 0 e 19." }
            require(recipe in 0..4) { "$name: recipe precisa estar entre 0 e 4." }
            require(envelope in 0..9) { "$name: envelope precisa estar entre 0 e 9." }
            val category=o.optString("category","Meus efeitos").trim().ifBlank {"Meus efeitos"}.take(40)
            val rawId=o.optString("id").trim().take(80)
            val id="custom-"+stableId(if(rawId.isBlank()) "$name|$category|$engine|$recipe|$envelope" else rawId)
            result+=FxPreset(id,name,category,engine,recipe,envelope)
        }
        require(result.isNotEmpty()) { "Nenhum efeito compatível foi encontrado no pacote." }
        return result.distinctBy {it.id}
    }

    private fun stableId(value:String):String {
        val digest=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") {"%02x".format(it)}
    }
}
