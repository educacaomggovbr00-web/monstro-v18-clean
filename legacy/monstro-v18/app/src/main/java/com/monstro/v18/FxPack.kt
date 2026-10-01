package com.monstro.v18

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest

/**
 * Imports Monstro FX packs and performs a safe best-effort conversion of
 * Premiere .prfpset XML files to equivalent Monstro render recipes.
 * Downloaded files never execute code inside the app.
 */
object FxPackParser {
    private const val MAX_EFFECTS = 200

    fun parse(source:String):List<FxPreset> {
        require(source.length <= 1_000_000) { "Pacote de efeitos muito grande (máximo 1 MB)." }
        val clean=source.trimStart()
        return if(clean.startsWith("<")) parsePremiereXml(source) else parseMonstroJson(source)
    }

    private fun parseMonstroJson(source:String):List<FxPreset> {
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

    private data class Family(val words:List<String>,val name:String,val category:String,val engine:Int,val recipe:Int,val envelope:Int)

    private val premiereFamilies=listOf(
        Family(listOf("gaussian blur","directional blur","blur"),"Blur Premiere","Blur",18,1,7),
        Family(listOf("shake","camera shake","wiggle"),"Shake Premiere","Shake",2,0,8),
        Family(listOf("transform","zoom","scale"),"Zoom / Transform Premiere","Motion",19,0,1),
        Family(listOf("chromatic","rgb split","rgb"),"RGB Split Premiere","RGB",1,0,6),
        Family(listOf("glitch","digital distortion"),"Glitch Premiere","Glitch",0,0,6),
        Family(listOf("glow","light leak","light"),"Glow / Light Premiere","Light",16,1,7),
        Family(listOf("grain","noise"),"Film Grain Premiere","Cinematic",14,0,9),
        Family(listOf("mirror","reflect"),"Mirror Premiere","Retro",8,0,9),
        Family(listOf("mosaic","pixelate","pixel"),"Pixel / Mosaic Premiere","Retro",9,0,9),
        Family(listOf("posterize"),"Posterize Premiere","Retro",11,0,9),
        Family(listOf("vhs","scanline","scan line"),"VHS Premiere","VHS",12,0,5),
        Family(listOf("prism"),"Prism Premiere","RGB",17,0,7),
        Family(listOf("wave","warp","distort","twirl"),"Distortion Premiere","Distortion",5,0,7)
    )

    private fun parsePremiereXml(source:String):List<FxPreset> {
        val lower=source.lowercase()
        require("preset" in lower || "premiere" in lower || "filter" in lower || "effect" in lower) {
            "Esse XML não parece ser um preset de efeito do Premiere."
        }
        val names=Regex("""(?i)(?:name|displayname)\s*=\s*["\']([^"\']{2,80})["\']""")
            .findAll(source).map {it.groupValues[1].trim()}.filter {it.isNotBlank()}.distinct().take(20).toList()
        val result=premiereFamilies.filter {family->family.words.any {it in lower}}.mapIndexed {index,family->
            val label=names.getOrNull(index)?.takeIf {it.length in 2..80 } ?: family.name
            FxPreset(
                id="custom-"+stableId("prfpset|$label|${family.engine}|${family.recipe}|${family.envelope}"),
                name=label,category="Premiere · ${family.category}",
                engine=family.engine,recipe=family.recipe,envelope=family.envelope
            )
        }
        require(result.isNotEmpty()) {
            "O .prfpset abriu, mas usa efeitos que o Monstro ainda não consegue converter."
        }
        return result.distinctBy {it.id}.take(MAX_EFFECTS)
    }

    private fun stableId(value:String):String {
        val digest=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") {"%02x".format(it)}
    }
}
