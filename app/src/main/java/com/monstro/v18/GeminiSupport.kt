package com.monstro.v18

object GeminiSupport {
    const val GENERAL_MODEL = "gemini-3.5-flash-lite"
    const val TTS_MODEL = "gemini-3.1-flash-tts-preview"

    fun userMessage(error:Throwable):String {
        val detail=generateSequence(error as Throwable?){it?.cause}
            .take(8).mapNotNull {it?.message}.joinToString(" ").lowercase()
        return when {
            "429" in detail || "quota" in detail || "resource_exhausted" in detail || "resource exhausted" in detail ->
                "A cota do Gemini deste projeto acabou ou está temporariamente ocupada."
            "app check" in detail || "permission_denied" in detail || "403" in detail ->
                "O Firebase bloqueou a IA pelo App Check. Verifique o registro do app e o Play Integrity no Firebase."
            "api key" in detail || "400" in detail ->
                "A configuração do Firebase deste APK não corresponde ao projeto atual."
            "network" in detail || "timeout" in detail || "unavailable" in detail || "socket" in detail ->
                "A IA online está sem conexão ou temporariamente indisponível."
            else -> "O Gemini não respondeu agora."
        }
    }
}
