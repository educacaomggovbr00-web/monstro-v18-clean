package com.monstro.v18

/** Twenty different algorithms, five spatial recipes and ten temporal envelopes. */
data class FxPreset(val id:String,val name:String,val category:String,val engine:Int,val recipe:Int,val envelope:Int) {
    val signature get() = Triple(engine,recipe,envelope)
}
object FxCatalog {
    private val engines=listOf(
        "Fragment" to "Glitch", "Chromatic" to "RGB", "Impact" to "Shake", "Punch" to "Trap",
        "Orbit" to "Motion", "Wave" to "Distortion", "Water" to "Distortion", "Vortex" to "Distortion",
        "Mirror" to "Anime", "Mosaic" to "Retro", "Ink" to "Anime", "Poster" to "Retro",
        "Scan" to "VHS", "Tracking" to "VHS", "Grain" to "Cinematic", "Duotone" to "Cinematic",
        "Leak" to "Light", "Prism" to "RGB", "Directional" to "Blur", "Tunnel" to "Motion")
    private val recipes=listOf("Micro","Wide","Split","Dense","Extreme")
    private val envelopes=listOf("Pulse","Rise","Fall","Bounce","Double","Drift","Snap","Breath","Beat","Hold")
    val all:List<FxPreset> = engines.flatMapIndexed { e,(name,cat)->recipes.flatMapIndexed { r,recipe->envelopes.mapIndexed { t,env->FxPreset("fx-$e-$r-$t","$name $recipe · $env",cat,e,r,t) } } }
    val categories=all.map { it.category }.distinct()
    private val index=all.associateBy { it.id }
    fun get(id:String)=index[id]
    fun search(query:String,category:String?=null)=all.filter { (category==null || it.category==category) && (query.isBlank() || it.name.contains(query,true) || it.category.contains(query,true)) }
}
