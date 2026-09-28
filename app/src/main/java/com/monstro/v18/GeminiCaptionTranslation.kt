package com.monstro.v18

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class GeminiCaptionTranslation(private val context:Context) {
    val configured:Boolean get()=FirebaseApp.getApps(context).isNotEmpty()

    suspend fun translate(track:SrtTrack,targetLanguage:String,progress:(Int)->Unit):SrtTrack {
        require(configured){"Firebase AI Logic não está configurado neste APK."}
        require(track.cues.isNotEmpty()){"Não há legendas para traduzir."}
        val model=Firebase.ai(backend=GenerativeBackend.googleAI()).generativeModel(
            modelName=GeminiSupport.GENERAL_MODEL,
            generationConfig=generationConfig {
                responseMimeType="application/json"
                maxOutputTokens=8192
            }
        )
        val result=track.cues.toMutableList()
        val batches=track.cues.withIndex().chunked(40)
        batches.forEachIndexed {batchIndex,batch->
            currentCoroutineContext().ensureActive()
            val input=JSONArray().also {array->
                batch.forEach {entry->array.put(JSONObject().put("index",entry.index).put("text",entry.value.text))}
            }
            val prompt=content {
                text(
                    """
                    Traduza as legendas abaixo para $targetLanguage.
                    Preserve o sentido, tom, gírias, nomes próprios e palavrões quando existirem.
                    Não resuma, não explique e não acrescente informações.
                    Retorne SOMENTE JSON válido no formato:
                    {"translations":[{"index":0,"text":"texto traduzido"}]}
                    Mantenha exatamente os mesmos índices recebidos.

                    Entrada:
                    $input
                    """.trimIndent()
                )
            }
            val raw=model.generateContent(prompt).text ?: error("Gemini não retornou a tradução.")
            val translated=parse(raw)
            batch.forEach {entry->
                val text=translated[entry.index]?.trim().orEmpty()
                if(text.isNotBlank()){
                    val cue=entry.value
                    result[entry.index]=cue.copy(text=text,wordTimes=translatedWordTimes(cue,text))
                }
            }
            progress(((batchIndex+1)*100/batches.size).coerceIn(1,100))
        }
        return SrtTrack(result)
    }

    private fun parse(raw:String):Map<Int,String> {
        val clean=raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root=JSONTokener(clean).nextValue()
        val array=when(root){
            is JSONObject -> root.optJSONArray("translations") ?: JSONArray()
            is JSONArray -> root
            else -> JSONArray()
        }
        val map=mutableMapOf<Int,String>()
        for(i in 0 until array.length()){
            val item=array.optJSONObject(i) ?: continue
            val index=item.optInt("index",-1)
            val text=item.optString("text").trim()
            if(index>=0 && text.isNotBlank())map[index]=text
        }
        require(map.isNotEmpty()){"Gemini respondeu sem traduções válidas."}
        return map
    }

    private fun translatedWordTimes(cue:SrtCue,text:String):List<WordTime> {
        val count=text.split(Regex("\\s+")).count {it.isNotBlank()}
        if(count<=0)return emptyList()
        if(cue.wordTimes.size==count)return cue.wordTimes
        val duration=(cue.endMs-cue.startMs).coerceAtLeast(1)
        return (0 until count).map {i->
            val start=cue.startMs+duration*i/count
            val end=cue.startMs+duration*(i+1)/count
            WordTime(start,maxOf(start+1,end))
        }
    }
}
