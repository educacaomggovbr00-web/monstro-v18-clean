package com.monstro.v18

data class FxPreset(
    val id:String,
    val name:String,
    val category:String,
    val engine:Int,
    val recipe:Int,
    val envelope:Int
) {
    val signature get()=Triple(engine,recipe,envelope)
}

/**
 * Curated FX library.
 * Only useful, readable presets are shown in the editor.
 * Legacy fx-e-r-t ids still resolve so older projects keep rendering.
 */
object FxCatalog {
    private val engineNames=listOf(
        "Fragment","Chromatic","Impact","Punch","Orbit","Wave","Water","Vortex",
        "Mirror","Mosaic","Ink","Poster","Scan","Tracking","Grain","Duotone",
        "Leak","Prism","Directional","Tunnel"
    )
    private val engineCategories=listOf(
        "Glitch","RGB","Shake","Trap","Motion","Distortion","Distortion","Distortion",
        "Anime","Retro","Anime","Retro","VHS","VHS","Cinematic","Cinematic",
        "Light","RGB","Blur","Motion"
    )
    private val recipeNames=listOf("Micro","Wide","Split","Dense","Extreme")
    private val envelopeNames=listOf("Pulse","Rise","Fall","Bounce","Double","Drift","Snap","Breath","Beat","Hold")

    private fun fx(engine:Int,recipe:Int,envelope:Int,name:String,category:String=engineCategories[engine]) =
        FxPreset("fx-"+engine+"-"+recipe+"-"+envelope,name,category,engine,recipe,envelope)

    val all:List<FxPreset> = listOf(
        fx(2,0,6,"Shake curto","Shake"),
        fx(2,0,8,"Shake no beat","Shake"),
        fx(2,1,0,"Shake suave","Shake"),
        fx(3,0,8,"Punch no beat","Trap"),
        fx(3,0,0,"Punch zoom","Trap"),

        fx(1,0,0,"RGB Split suave","RGB"),
        fx(1,0,6,"RGB Impact","RGB"),
        fx(17,0,7,"Prisma leve","RGB"),

        fx(0,0,6,"Glitch rápido","Glitch"),
        fx(0,0,8,"Glitch no beat","Glitch"),
        fx(0,1,0,"Glitch horizontal","Glitch"),

        fx(4,0,7,"Movimento orbital leve","Motion"),
        fx(19,0,1,"Zoom túnel suave","Motion"),

        fx(18,0,9,"Blur direcional","Blur"),
        fx(18,1,7,"Motion blur estilizado","Blur"),

        fx(16,0,1,"Light leak entrada","Light"),
        fx(16,0,0,"Light leak pulsante","Light"),
        fx(16,1,7,"Glow de luz","Light"),

        fx(14,0,9,"Grão cinematográfico","Cinematic"),
        fx(15,0,9,"Duotone cinema","Cinematic"),
        fx(15,1,7,"Color grade estilizado","Cinematic"),

        fx(12,0,5,"Scanlines VHS","VHS"),
        fx(13,0,6,"Tracking VHS","VHS"),

        fx(9,0,9,"Pixel leve","Retro"),
        fx(11,0,9,"Posterize","Retro"),
        fx(8,0,9,"Espelho","Retro")
    )

    val categories=all.map {it.category}.distinct()
    private val index=all.associateBy {it.id}

    fun get(id:String):FxPreset? = index[id] ?: parseLegacy(id)

    fun search(query:String,category:String?=null):List<FxPreset> =
        all.filter {
            (category==null || it.category==category) &&
                (query.isBlank() || it.name.contains(query,true) || it.category.contains(query,true))
        }

    fun isCurated(id:String)=id in index

    fun curatedReplacement(id:String):FxPreset? {
        index[id]?.let {return it}
        val legacy=parseLegacy(id) ?: return null
        return all.filter {it.engine==legacy.engine}.minByOrNull {
            kotlin.math.abs(it.recipe-legacy.recipe)*10+kotlin.math.abs(it.envelope-legacy.envelope)
        } ?: all.firstOrNull {it.category==legacy.category}
    }

    fun sanitizeProject(project:StudioProject):StudioProject {
        val remapped=project.fx.mapNotNull {layer->
            curatedReplacement(layer.presetId)?.let {preset->
                layer.copy(presetId=preset.id,intensity=layer.intensity.coerceIn(.1f,1.35f))
            }
        }
        fun remapIds(ids:Collection<String>)=ids.mapNotNull {curatedReplacement(it)?.id}.distinct()
        return project.copy(
            fx=remapped,
            favorites=remapIds(project.favorites).toSet(),
            recent=remapIds(project.recent).take(20)
        )
    }

    private fun parseLegacy(id:String):FxPreset? {
        val parts=id.removePrefix("fx-").split("-")
        if(parts.size!=3)return null
        val e=parts[0].toIntOrNull() ?: return null
        val r=parts[1].toIntOrNull() ?: return null
        val t=parts[2].toIntOrNull() ?: return null
        if(e !in engineNames.indices || r !in recipeNames.indices || t !in envelopeNames.indices)return null
        return FxPreset(
            id=id,
            name=engineNames[e]+" "+recipeNames[r]+" · "+envelopeNames[t],
            category=engineCategories[e],
            engine=e,recipe=r,envelope=t
        )
    }
}
