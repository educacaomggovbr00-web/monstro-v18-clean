package com.monstro.v18.monstro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.FirebaseApp
import com.monstro.v18.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MonstroToolsDialog(model:EditorViewModel,onClose:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val state by model.state.collectAsStateWithLifecycle()
    val progress by model.monstroProgress.collectAsStateWithLifecycle()
    var query by remember{mutableStateOf("")}
    var text by remember{mutableStateOf("")}
    var style by remember{mutableStateOf("Trap cinematográfico")}
    var language by remember{mutableStateOf("Português brasileiro")}
    var account by remember{mutableStateOf(false)}
    var custom by remember{mutableStateOf(emptyList<FxPreset>())}
    LaunchedEffect(context) {
        custom=withContext(Dispatchers.IO) {
            runCatching {FxPackParser.parse(File(context.filesDir,"monstro-fxpack.json").readText())}
                .getOrDefault(emptyList())
        }
    }
    val presets=remember(query,custom){
        val all=FxCatalog.all+custom+(0..19).flatMap{e->(0..4).flatMap{r->(0..9).mapNotNull{t->FxCatalog.get("fx-$e-$r-$t")}}}
        all.distinctBy{it.id}.filter{query.isBlank() || it.name.contains(query,true) || it.category.contains(query,true)}
    }
    val importPack=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)scope.launch {
            runCatching {withContext(Dispatchers.IO) {
                val source=context.contentResolver.openInputStream(uri)!!.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=1_000_000);out.write(buffer,0,n)};out.toString("UTF-8")}
                val parsed=FxPackParser.parse(source)
                // Save canonical JSON even when the input was a Premiere XML.
                val json=org.json.JSONArray().also{a->parsed.forEach{p->a.put(org.json.JSONObject().put("name",p.name).put("category",p.category).put("engine",p.engine).put("recipe",p.recipe).put("envelope",p.envelope))}}
                File(context.filesDir,"monstro-fxpack.json").writeText(json.toString());parsed
            }}.onSuccess{custom=it}.onFailure{
                if(it is kotlinx.coroutines.CancellationException)throw it
                model.showToast("Pacote: ${it.localizedMessage}")
            }
        }
    }
    val busy=progress!=null
    val configured=remember{FirebaseApp.getApps(context).isNotEmpty()}
    if(account && configured)AccountDialog{account=false}
    AlertDialog(onDismissRequest=onClose,title={Text("Monstro V18 · ferramentas")},text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Efeitos e IA do Monstro na mesma timeline do motor ClearCut.")
            progress?.let{Text(it);LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=model::cancelMonstroOperation){Text("Cancelar operação")}}
            Button(onClick={model.monstroCaptions(false)},enabled=!busy){Text("Legendas offline · Vosk português")}
            Text("Vosk baixa o modelo uma vez e reconhece no aparelho. Gemini envia áudio ou quadros ao serviço configurado no Firebase.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={model.monstroCaptions(true)},enabled=!busy && configured){Text("Legendas Gemini · fallback offline")}
            OutlinedTextField(style,{style=it},label={Text("Estilo para Auto Edit")})
            Button(onClick={model.monstroAutoEdit(style)},enabled=!busy && configured){Text("Auto Edit · Gemini")}
            OutlinedTextField(language,{language=it},label={Text("Idioma da tradução")})
            Button(onClick={model.monstroTranslate(language)},enabled=!busy && configured && state.selectedClipId!=null){Text("Traduzir legendas do clipe · Gemini")}
            OutlinedTextField(text,{text=it.take(5000)},label={Text("Texto da narração")})
            Button(onClick={model.monstroNarration(text)},enabled=!busy && configured && text.isNotBlank()){Text("Narrar com Gemini e adicionar áudio")}
            OutlinedButton(onClick={account=true},enabled=configured){Text("Conta / perfil Monstro")}
            HorizontalDivider()
            OutlinedTextField(query,{query=it},label={Text("Buscar Chaos / Studio FX")})
            OutlinedButton(onClick={importPack.launch(arrayOf("application/json","application/xml","text/xml","text/plain","application/octet-stream"))},enabled=!busy){Text("Importar pacote Monstro / Premiere")}
            Text("${presets.size} presets · selecione um clipe para aplicar")
            LazyColumn(Modifier.height(180.dp)) {
                items(presets,key={it.id}){p->TextButton(onClick={model.applyMonstroPreset(p)},enabled=!busy && state.selectedClipId!=null){Text("${p.category} · ${p.name}")}}
            }
        }
    },confirmButton={TextButton(onClick=onClose){Text("Fechar")}})
}
