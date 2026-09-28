package com.monstro.v18

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun MonstroApp(model:EditorModel){
    var screen by remember {mutableStateOf("home")}
    var tab by remember {mutableStateOf("Editar")}
    var accountDialog by remember {mutableStateOf(false)}
    var pendingAction by remember {mutableStateOf<String?>(null)}

    val videoPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        if(uris.isNotEmpty()){
            model.importVideos(uris)
            screen="studio"
        } else pendingAction=null
    }

    fun withProject(action:String){
        if(model.clips.isEmpty()){
            pendingAction=action
            videoPicker.launch(arrayOf("video/*"))
        }else{
            when{
                action.startsWith("auto:")->model.runAiAutoEdit(action.removePrefix("auto:"))
                action=="caption"->{model.focus("Legenda");model.autoCaption()}
                action.startsWith("focus:")->model.focus(action.removePrefix("focus:"))
            }
            screen="studio"
        }
    }

    LaunchedEffect(model.importing,model.clips.size,pendingAction){
        val action=pendingAction ?: return@LaunchedEffect
        if(!model.importing && model.clips.isNotEmpty()){
            pendingAction=null
            when{
                action.startsWith("auto:")->model.runAiAutoEdit(action.removePrefix("auto:"))
                action=="caption"->{model.focus("Legenda");model.autoCaption()}
                action.startsWith("focus:")->model.focus(action.removePrefix("focus:"))
            }
        }
    }

    if(screen=="studio"){
        StudioScreen(model,onBack={screen="home"})
    }else{
        MaterialTheme(colorScheme=lightColorScheme(
            primary=Color(0xff7d36e8),
            background=Color(0xfff7f8fc),
            surface=Color.White,
            onBackground=Color(0xff111216),
            onSurface=Color(0xff111216)
        )){
        Surface(Modifier.fillMaxSize(),color=Color(0xfff7f8fc),contentColor=Color(0xff111216)){
            Column(Modifier.fillMaxSize()){
                Box(Modifier.weight(1f)){
                    when(tab){
                        "Modelos"->ModelsHome(model,::withProject)
                        "Lab. IA"->AiLabHome(model,::withProject)
                        "Projetos"->ProjectsHome(model,{screen="studio"},{pendingAction=null;videoPicker.launch(arrayOf("video/*"))})
                        "Eu"->ProfileHome(onAccount={accountDialog=true})
                        else->EditHome(model,::withProject,{pendingAction=null;videoPicker.launch(arrayOf("video/*"))},{screen="studio"})
                    }
                }
                NavigationBar(containerColor=Color.White){
                    listOf("Editar","Modelos","Lab. IA","Projetos","Eu").forEach {item->
                        NavigationBarItem(
                            selected=tab==item,
                            onClick={tab=item},
                            icon={Text(when(item){"Editar"->"✂";"Modelos"->"▣";"Lab. IA"->"✦";"Projetos"->"▱";else->"●"},fontSize=18.sp)},
                            label={Text(item,fontSize=10.sp)}
                        )
                    }
                }
            }
        }
        }
    }
    if(accountDialog)AccountDialog{accountDialog=false}
}

@Composable
private fun EditHome(model:EditorModel,action:(String)->Unit,newVideo:()->Unit,continueEdit:()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("MONSTRO",fontWeight=FontWeight.Black,fontSize=28.sp,letterSpacing=2.sp,color=Color(0xff111216))
                Text("Crie. Corte. Destrua o padrão.",fontSize=12.sp,color=Color.Gray)
            }
            Surface(shape=RoundedCornerShape(18.dp),color=Color(0xffece9ff)){Text("V18",modifier=Modifier.padding(horizontal=14.dp,vertical=8.dp),color=Color(0xff7a2de2),fontWeight=FontWeight.Bold)}
        }
        Surface(shape=RoundedCornerShape(28.dp),color=Color(0xffdff0ff),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                Text("Video create",fontWeight=FontWeight.Bold,color=Color(0xff46536a))
                Text("Comece seu próximo edit",fontSize=30.sp,fontWeight=FontWeight.Black,color=Color(0xff10131a))
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Button(onClick=newVideo,modifier=Modifier.weight(1f).height(112.dp),shape=RoundedCornerShape(22.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("+",fontSize=34.sp);Text("Novo vídeo")}}
                    OutlinedButton(onClick=if(model.clips.isNotEmpty())continueEdit else newVideo,modifier=Modifier.weight(1f).height(112.dp),shape=RoundedCornerShape(22.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("▶",fontSize=28.sp);Text(if(model.clips.isNotEmpty())"Continuar" else "Importar")}}
                }
            }
        }
        if(model.clips.isNotEmpty()){
            Text("Projeto recente",fontWeight=FontWeight.Bold,fontSize=18.sp,color=Color(0xff111216))
            ElevatedCard(onClick=continueEdit,modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                    Surface(Modifier.size(70.dp),shape=RoundedCornerShape(14.dp),color=Color(0xff17151f)){Box(contentAlignment=Alignment.Center){Text("▶",color=Color(0xffa855f7))}}
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)){
                        Text(model.current?.name ?: "Projeto Monstro",fontWeight=FontWeight.Bold,maxLines=1)
                        Text("${model.clips.size} clipe(s) · ${timeLabel(model.totalDuration)}",fontSize=12.sp,color=Color.Gray)
                    }
                    Text("›",fontSize=28.sp)
                }
            }
        }
        Text("Ferramentas",fontWeight=FontWeight.Bold,fontSize=20.sp,color=Color(0xff111216))
        ToolRows(listOf(
            Triple("AutoCut","Cortes, ritmo e efeitos","auto:Automático"),
            Triple("Legendas automáticas","Gemini + Offline","caption"),
            Triple("Ajustar velocidade","Curvas e keyframes","focus:Vídeo"),
            Triple("Ferramentas de áudio","Voiceover, pitch, fades","focus:Áudio"),
            Triple("Efeitos","Biblioteca de 1000 FX","focus:FX"),
            Triple("Camadas","Vídeo, imagem, texto","focus:Camada"),
            Triple("Proporção","9:16 · 16:9 · 1:1 · 4:5","focus:Proporção"),
            Triple("Ajustes","Cor, HSL e temperatura","focus:Ajustes")
        ),action)
    }
}

@Composable
private fun ToolRows(items:List<Triple<String,String,String>>,action:(String)->Unit){
    items.chunked(2).forEach {row->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            row.forEach {(title,sub,code)->
                ElevatedCard(onClick={action(code)},modifier=Modifier.weight(1f)){
                    Column(Modifier.padding(14.dp).heightIn(min=72.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(title,fontWeight=FontWeight.Bold,color=Color(0xff18191e))
                        Text(sub,fontSize=10.sp,color=Color.Gray)
                    }
                }
            }
            if(row.size==1)Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModelsHome(model:EditorModel,action:(String)->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Modelos",fontSize=30.sp,fontWeight=FontWeight.Black,color=Color(0xff111216))
        Text("Presets inteligentes aplicados ao seu projeto. Tudo continua editável na timeline.",color=Color.Gray)
        listOf("Trap / Música","Cinemático","Vlog / Conversa","Gameplay / Ação","Anime / Edit").forEach {style->
            ElevatedCard(onClick={action("auto:$style")},modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
                    Text("✦",fontSize=26.sp,color=Color(0xff8b35e6));Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)){Text(style,fontWeight=FontWeight.Bold);Text(if(model.clips.isEmpty())"Escolha um vídeo para aplicar" else "Aplicar no projeto atual",fontSize=11.sp,color=Color.Gray)}
                    Text("›",fontSize=26.sp)
                }
            }
        }
    }
}

@Composable
private fun AiLabHome(model:EditorModel,action:(String)->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("Lab. de IA",fontSize=30.sp,fontWeight=FontWeight.Black,color=Color(0xff111216))
        Text("Ferramentas de IA que já funcionam no Monstro.",color=Color.Gray)
        ToolRows(listOf(
            Triple("IA Auto Edit","Entende frames, fala e ritmo","auto:Automático"),
            Triple("AutoCut","Monta edição por contexto","auto:Trap / Música"),
            Triple("Legendas automáticas","Gemini com fallback offline","caption"),
            Triple("Narração IA","Gemini TTS no painel Texto","focus:Texto"),
            Triple("Auto-Beats","Detecta picos no áudio","focus:Áudio"),
            Triple("FX inteligente","Escolha contextual via Auto Edit","auto:Anime / Edit")
        ),action)
        Surface(shape=RoundedCornerShape(18.dp),color=Color(0xffece9ff),modifier=Modifier.fillMaxWidth()){
            Text("Quando a cota do Gemini acabar, Auto Edit e legendas usam fallback local/offline em vez de travar.",modifier=Modifier.padding(16.dp),fontSize=12.sp,color=Color(0xff56318d))
        }
    }
}

@Composable
private fun ProjectsHome(model:EditorModel,openEditor:()->Unit,newVideo:()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text("Projetos",fontSize=30.sp,fontWeight=FontWeight.Black,color=Color(0xff111216),modifier=Modifier.weight(1f));Button(onClick=newVideo){Text("+ Novo")}}
        if(model.clips.isEmpty()){
            Surface(shape=RoundedCornerShape(20.dp),color=Color.White,modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("Nenhum projeto aberto",fontWeight=FontWeight.Bold);Text("Importe um vídeo para começar.",color=Color.Gray);Spacer(Modifier.height(12.dp));Button(onClick=newVideo){Text("Criar projeto")}}
            }
        }else{
            Text("Local",fontWeight=FontWeight.Bold,color=Color(0xff111216))
            ElevatedCard(onClick=openEditor,modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                    Surface(Modifier.size(82.dp),shape=RoundedCornerShape(14.dp),color=Color(0xff14131c)){Box(contentAlignment=Alignment.Center){Text("▶",color=Color(0xffa855f7),fontSize=28.sp)}}
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)){
                        Text(model.clips.firstOrNull()?.name ?: "Projeto Monstro",fontWeight=FontWeight.Bold,maxLines=1)
                        Text("${model.clips.size} mídia(s) · ${timeLabel(model.totalDuration)}",color=Color.Gray,fontSize=12.sp)
                        Text("Salvo automaticamente neste aparelho",color=Color(0xff8b35e6),fontSize=10.sp)
                    }
                    Text("›",fontSize=28.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileHome(onAccount:()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("Eu",fontSize=30.sp,fontWeight=FontWeight.Black,color=Color(0xff111216))
        ElevatedCard(onClick=onAccount,modifier=Modifier.fillMaxWidth()){
            Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){
                Surface(Modifier.size(58.dp),shape=RoundedCornerShape(29.dp),color=Color(0xffece9ff)){Box(contentAlignment=Alignment.Center){Text("M",fontWeight=FontWeight.Black,color=Color(0xff8b35e6),fontSize=22.sp)}}
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)){Text("Conta Monstro",fontWeight=FontWeight.Bold);Text("Entrar · criar conta · logout",fontSize=11.sp,color=Color.Gray)}
                Text("›",fontSize=28.sp)
            }
        }
        Surface(shape=RoundedCornerShape(18.dp),color=Color.White,modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Monstro V18 Studio",fontWeight=FontWeight.Bold)
                Text("Projetos locais têm salvamento automático. Conta Firebase fica disponível quando o método de autenticação estiver ativado no projeto.",fontSize=12.sp,color=Color.Gray)
            }
        }
    }
}
