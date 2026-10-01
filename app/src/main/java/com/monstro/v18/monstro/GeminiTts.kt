package com.monstro.v18.monstro

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GeneratedNarration(val file:File,val durationMs:Long)

@OptIn(PublicPreviewAPI::class)
class GeminiTts(private val context:Context){
    val configured get()=FirebaseApp.getApps(context).isNotEmpty()

    suspend fun generate(text:String,voice:String,style:String):GeneratedNarration{
        require(configured){"Firebase AI Logic não está conectado."}
        val clean=text.trim()
        require(clean.isNotEmpty()){"Digite um texto para narrar."}
        require(clean.length<=5000){"A narração aceita até 5.000 caracteres por vez."}

        val config=generationConfig {
            responseModalities=listOf(ResponseModality.AUDIO)
            speechConfig=SpeechConfig(voice=Voice(voice),languageCode="pt-BR")
        }
        val model=Firebase.ai(backend=GenerativeBackend.googleAI()).generativeModel(
            modelName=GeminiSupport.TTS_MODEL,
            generationConfig=config
        )
        val instruction=when(style){
            "Animado"->"Fale em português do Brasil, com energia, ritmo de vídeo curto e entonação expressiva."
            "Calmo"->"Fale em português do Brasil, com voz calma, natural, pausas leves e ritmo confortável."
            "Narrador"->"Fale em português do Brasil como um narrador profissional, claro, firme e cinematográfico."
            else->"Fale em português do Brasil de forma natural, clara e humana."
        }
        val response=model.generateContent("$instruction\n\nLeia exatamente este texto, sem acrescentar conteúdo:\n$clean")
        val part=response.candidates.asSequence().flatMap {it.content.parts.asSequence()}.filterIsInstance<InlineDataPart>().firstOrNull()
            ?: error("Gemini não retornou áudio.")
        val pcm=part.inlineData
        require(pcm.isNotEmpty()){"Gemini retornou áudio vazio."}

        val dir=File(context.filesDir,"tts").apply {mkdirs()}
        val file=File(dir,"narracao-${System.currentTimeMillis()}.wav")
        writeWav(file,pcm,24000,1,16)
        val duration=(pcm.size.toLong()*1000L/(24000L*2L)).coerceAtLeast(1)
        return GeneratedNarration(file,duration)
    }

    private fun writeWav(file:File,pcm:ByteArray,sampleRate:Int,channels:Int,bits:Int){
        val byteRate=sampleRate*channels*bits/8
        val blockAlign=channels*bits/8
        val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36+pcm.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bits.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }
        FileOutputStream(file).use {out->out.write(header.array());out.write(pcm)}
    }
}
