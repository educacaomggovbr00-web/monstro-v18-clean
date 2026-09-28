package com.monstro.v18

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun StudioScreen(m:EditorModel){
    val videos=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(),m::importVideos)
    val audio=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),m::importAudio)
    val image=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),m::importImage)
    val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)m.startVoiceover()else m.showMessage("Permita acesso ao microfone para gravar dublagem.")}
    val srt=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),m::importLyrics)
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4"),m::saveOutput)
    var library by remember {mutableStateOf(false)};var exportDialog by remember {mutableStateOf(false)};var mediaDialog by remember {mutableStateOf(false)};var aiDialog by remember {mutableStateOf(false)};var accountDialog by remember {mutableStateOf(false)};var fullscreen by remember {mutableStateOf(false)}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,m){val listener=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP)m.pauseAll()};owner.lifecycle.addObserver(listener);onDispose{owner.lifecycle.removeObserver(listener)}}
    LaunchedEffect(m){while(true){m.tick();delay(if(m.player.isPlaying || m.busy)33 else 120)}}
    Column(Modifier.fillMaxSize().background(Color(0xff09090f)).systemBarsPadding()){
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("MONSTRO",fontWeight=FontWeight.Black,letterSpacing=2.sp,fontSize=17.sp);Text("V18  /  STUDIO",color=MaterialTheme.colorScheme.primary,fontSize=8.sp,letterSpacing=2.sp)}
            TextButton(onClick={accountDialog=true},contentPadding=PaddingValues(horizontal=8.dp)){Text("Conta",fontSize=11.sp)}
            Button(onClick={exportDialog=true},enabled=m.clips.isNotEmpty()&&!m.busy,contentPadding=PaddingValues(horizontal=11.dp)){Text("Exportar")}
        }
        LazyRow(Modifier.fillMaxWidth().height(38.dp).background(Color(0xff111119)),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically){
            item{TextButton(onClick={mediaDialog=true},enabled=!m.busy){Text("+ Mídia",fontSize=11.sp)}}
            item{TextButton(onClick={aiDialog=true},enabled=m.clips.isNotEmpty()&&!m.busy){Text("✦ IA Auto Edit",fontSize=11.sp,fontWeight=FontWeight.Bold)}}
            item{TextButton(onClick={exportDialog=true},enabled=!m.busy){Text("${m.exportFormat.resolutionLabel} · 30 fps",fontSize=10.sp)}}
            item{Text("${m.canvasRatio} · ${m.studio.fx.size} FX",fontSize=10.sp,color=Color.Gray,modifier=Modifier.padding(horizontal=8.dp))}
        }
        Box(Modifier.fillMaxWidth().heightIn(min=140.dp,max=320.dp).weight(1.65f).clipToBounds().background(Color.Black),contentAlignment=Alignment.Center){
            if(m.current==null)Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Seu próximo edit começa aqui",fontWeight=FontWeight.Bold);TextButton(onClick={videos.launch(arrayOf("video/*"))}){Text("+ Importar vídeos")}}
            else if(fullscreen)Box(Modifier.fillMaxHeight().aspectRatio(m.exportFormat.aspect),contentAlignment=Alignment.Center){Text("PRÉVIA EM TELA CHEIA",color=Color.DarkGray,fontSize=10.sp)}
            else Box(Modifier.fillMaxHeight().aspectRatio(m.exportFormat.aspect)){
                key(m.player,fullscreen){AndroidView(factory={EditorPlayerView(it).apply {useController=false;resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT}},update={it.bind(m.player,!m.compatibilityPreview)},modifier=Modifier.fillMaxSize())}
                AndroidView(factory={StudioPreview(it)},update={it.model=m;it.invalidate()},modifier=Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
            Text(timeLabel(m.playhead),color=Color.White,fontSize=11.sp);Text(" / ${timeLabel(m.totalDuration)}",color=Color.Gray,fontSize=10.sp,modifier=Modifier.weight(1f))
            TextButton(onClick=m::undo,enabled=m.canUndo&&!m.busy,contentPadding=PaddingValues(5.dp)){Text("↶",fontSize=20.sp)}
            TextButton(onClick=m::redo,enabled=m.canRedo&&!m.busy,contentPadding=PaddingValues(5.dp)){Text("↷",fontSize=20.sp)}
            TextButton(onClick=m::addTimelineMarker,enabled=m.current!=null&&!m.busy,contentPadding=PaddingValues(5.dp)){Text("◆+",fontSize=13.sp)}
            TextButton(onClick={m.seekTimeline(m.playhead-100)},enabled=!m.busy,contentPadding=PaddingValues(5.dp)){Text("−.1")}
            TextButton(onClick={if(m.player.isPlaying)m.pauseAll()else {if(m.player.playbackState==androidx.media3.common.Player.STATE_ENDED)m.seekTimeline(0);m.player.play()}},enabled=m.current!=null&&!m.busy,contentPadding=PaddingValues(5.dp)){Text(if(m.player.isPlaying)"Ⅱ" else "▶",fontSize=20.sp)}
            TextButton(onClick={m.seekTimeline(m.playhead+100)},enabled=!m.busy,contentPadding=PaddingValues(5.dp)){Text("+.1")}
            TextButton(onClick={m.pauseAll();fullscreen=true},enabled=m.current!=null&&!m.busy,contentPadding=PaddingValues(5.dp)){Text("⛶",fontSize=18.sp)}
        }
        AndroidView(factory={StudioTimeline(it)},update={it.model=m;it.invalidate()},modifier=Modifier.fillMaxWidth().height(224.dp))
        Text("Arraste a régua para buscar · dois dedos para ampliar",color=Color.Gray,fontSize=9.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=2.dp))
        LazyRow(Modifier.fillMaxWidth().background(Color(0xff18151f)),horizontalArrangement=Arrangement.spacedBy(1.dp)){
            val tabs=listOf("✂ Editar" to "Vídeo","♪ Áudio" to "Áudio","T Texto" to "Texto","CC Legendas" to "Legenda","✦ Efeitos" to "FX","▱ Camada" to "Camada","◐ Filtros" to "Filtros","▣ Proporção" to "Proporção","☼ Ajustes" to "Ajustes")
            items(tabs){(label,kind)->TextButton(onClick={m.focus(kind)},enabled=!m.busy,contentPadding=PaddingValues(horizontal=9.dp,vertical=6.dp)){Text(label,color=if(m.inspector==kind)MaterialTheme.colorScheme.primary else Color.LightGray,fontWeight=FontWeight.Bold,fontSize=11.sp)}}
        }
        Column(Modifier.fillMaxWidth().weight(1.35f).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(m.busy){
                if(m.speechBusy)Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=Color(0xff15131e)){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Text("Analisando áudio",fontWeight=FontWeight.Bold,fontSize=18.sp)
                        Text("Reconhecendo a fala e sincronizando as palavras com o vídeo.",color=Color.LightGray,fontSize=12.sp)
                        m.speechProgress?.let {p->
                            LinearProgressIndicator(progress=p/100f,modifier=Modifier.fillMaxWidth())
                            Text("$p%",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                        } ?: LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                        Text(m.speechStatus,color=Color.Gray,fontSize=11.sp)
                        TextButton(onClick=m::cancelSpeech){Text("Cancelar")}
                    }
                } else if(m.ttsBusy)Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=Color(0xff15131e)){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Text("Criando narração IA",fontWeight=FontWeight.Bold,fontSize=18.sp)
                        Text("Gemini está sintetizando a voz em português do Brasil.",color=Color.LightGray,fontSize=12.sp)
                        LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                        Text(m.ttsStatus,color=Color.Gray,fontSize=11.sp)
                        TextButton(onClick=m::cancelNarration){Text("Cancelar")}
                    }
                } else if(m.aiEditBusy)Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=Color(0xff15131e)){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Text("IA Auto Edit",fontWeight=FontWeight.Bold,fontSize=18.sp)
                        Text("Entendendo cenas, fala, ritmo e escolhendo os efeitos do Monstro.",color=Color.LightGray,fontSize=12.sp)
                        LinearProgressIndicator(progress=m.aiEditProgress/100f,modifier=Modifier.fillMaxWidth())
                        Text("${m.aiEditProgress}% · ${m.aiEditStatus}",color=Color.Gray,fontSize=11.sp)
                        TextButton(onClick=m::cancelAiAutoEdit){Text("Cancelar")}
                    }
                } else if(m.beatBusy)Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=Color(0xff15131e)){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Text("Auto-Beats",fontWeight=FontWeight.Bold,fontSize=18.sp)
                        Text("Detectando picos e criando marcadores magnéticos.",color=Color.LightGray,fontSize=12.sp)
                        LinearProgressIndicator(progress=m.beatProgress/100f,modifier=Modifier.fillMaxWidth())
                        Text(m.beatStatus,color=Color.Gray,fontSize=11.sp)
                        TextButton(onClick=m::cancelAutoBeats){Text("Cancelar")}
                    }
                } else {
                    LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                    Text(if(m.exporting)"Renderizando ${m.progress?.let {"$it%"} ?: "…"}" else "Preparando…")
                    TextButton(onClick=m::cancelExport){Text("Cancelar")}
                }
            }
            else when(m.inspector){
                "Vídeo"->VideoInspector(m){videos.launch(arrayOf("video/*"))}
                "Áudio"->AudioInspector(m,{audio.launch(arrayOf("audio/*"))},{mic.launch(android.Manifest.permission.RECORD_AUDIO)})
                "Texto"->TextInspector(m)
                "Legenda"->CaptionInspector(m){srt.launch(arrayOf("*/*"))}
                "FX"->FxInspector(m){library=true}
                "Camada"->LayerInspector(m,{videos.launch(arrayOf("video/*"))},{image.launch(arrayOf("image/*"))},{audio.launch(arrayOf("audio/*"))})
                "Filtros"->FilterInspector(m)
                "Proporção"->RatioInspector(m)
                "Ajustes"->AdjustInspector(m)
            }
            if(m.output!=null)OutlinedButton(onClick={save.launch("MONSTRO_Studio.mp4")},enabled=!m.busy,modifier=Modifier.fillMaxWidth()){Text("Salvar último MP4")}
        }
    }
    if(library)FxLibrary(m){library=false}
    if(accountDialog)AccountDialog{accountDialog=false}
    if(aiDialog){
        var style by remember {mutableStateOf("Automático")}
        AlertDialog(
            onDismissRequest={aiDialog=false},
            title={Text("✦ Monstro IA Auto Edit")},
            text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("A IA analisa os quadros, a fala e os beats e aplica uma edição que você pode desfazer com ↶.",fontSize=12.sp,color=Color.LightGray)
                Text("Estilo",fontWeight=FontWeight.Bold)
                Choices(listOf("Automático","Trap / Música","Cinemático","Vlog / Conversa","Gameplay / Ação","Anime / Edit"),style){style=it}
                Text("Ela não apaga seus efeitos existentes: adiciona e ajusta em cima do projeto atual.",fontSize=10.sp,color=Color.Gray)
            }},
            confirmButton={Button(onClick={aiDialog=false;m.runAiAutoEdit(style)}){Text("Analisar e editar")}},
            dismissButton={TextButton(onClick={aiDialog=false}){Text("Cancelar")}}
        )
    }
    if(mediaDialog)AlertDialog(
        onDismissRequest={mediaDialog=false},
        title={Text("Adicionar mídia")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick={mediaDialog=false;videos.launch(arrayOf("video/*"))},modifier=Modifier.fillMaxWidth()){Text("Vídeo")}
            OutlinedButton(onClick={mediaDialog=false;image.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth()){Text("Imagem")}
            OutlinedButton(onClick={mediaDialog=false;audio.launch(arrayOf("audio/*"))},modifier=Modifier.fillMaxWidth()){Text("Áudio")}
        }},
        confirmButton={},
        dismissButton={TextButton(onClick={mediaDialog=false}){Text("Cancelar")}}
    )
    if(exportDialog)AlertDialog(onDismissRequest={exportDialog=false},title={Text("Exportar edit")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Proporção",fontWeight=FontWeight.Bold);Choices(listOf("9:16","16:9","1:1","4:5"),m.canvasRatio,m::selectCanvasRatio)
        Text("Fundo",fontWeight=FontWeight.Bold);Choices(listOf("Desfoque","Cor sólida","Padrão"),when(m.canvasBackground){"solid"->"Cor sólida";"pattern"->"Padrão";else->"Desfoque"}){m.selectCanvasBackground(when(it){"Cor sólida"->"solid";"Padrão"->"pattern";else->"blur"})}
        Text("Resolução",fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=m.safeMode,onClick={if(!m.safeMode)m.toggleSafeMode()},label={Text("540p · leve")});FilterChip(selected=!m.safeMode,onClick={if(m.safeMode)m.toggleSafeMode()},label={Text("1080p · 30fps")})}
        Text("Bitrate",fontWeight=FontWeight.Bold);Choices(listOf("Baixo","Recomendado","Alto"),when(m.bitrateMode){"low"->"Baixo";"high"->"Alto";else->"Recomendado"}){m.selectBitrateMode(when(it){"Baixo"->"low";"Alto"->"high";else->"recommended"})}
        Text("Codec",fontWeight=FontWeight.Bold);Choices(if(m.hevcSupported)listOf("H.264","HEVC") else listOf("H.264"),if(m.exportCodec=="HEVC")"HEVC" else "H.264"){m.selectExportCodec(if(it=="HEVC")"HEVC" else "H264")}
        Text("${m.exportFormat.width} × ${m.exportFormat.height} · 30 fps · ${String.format(java.util.Locale.US,"%.1f",m.exportFormat.bitrate/1_000_000f)} Mbps",fontSize=11.sp,color=Color.Gray)
    }},confirmButton={Button(onClick={exportDialog=false;m.export()}){Text("Gerar MP4")}},dismissButton={TextButton(onClick={exportDialog=false}){Text("Voltar")}})
    if(fullscreen && m.current!=null)Dialog(onDismissRequest={fullscreen=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding(),contentAlignment=Alignment.Center){
            Box(Modifier.fillMaxSize().padding(8.dp),contentAlignment=Alignment.Center){
                Box(Modifier.fillMaxWidth().aspectRatio(m.exportFormat.aspect)){
                    key(m.player,fullscreen){AndroidView(factory={EditorPlayerView(it).apply {useController=false;resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT}},update={it.bind(m.player,!m.compatibilityPreview)},modifier=Modifier.fillMaxSize())}
                    AndroidView(factory={StudioPreview(it)},update={it.model=m;it.invalidate()},modifier=Modifier.fillMaxSize())
                }
            }
            TextButton(onClick={fullscreen=false},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)){Text("Fechar ⛶")}
            Surface(modifier=Modifier.align(Alignment.BottomCenter).padding(18.dp),shape=RoundedCornerShape(24.dp),color=Color(0xaa15151c)){
                Row(Modifier.padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(timeLabel(m.playhead),fontSize=11.sp)
                    TextButton(onClick={m.seekTimeline(m.playhead-1000)}){Text("−1s")}
                    TextButton(onClick={if(m.player.isPlaying)m.pauseAll()else m.player.play()}){Text(if(m.player.isPlaying)"Ⅱ" else "▶",fontSize=22.sp)}
                    TextButton(onClick={m.seekTimeline(m.playhead+1000)}){Text("+1s")}
                    Text(timeLabel(m.totalDuration),fontSize=11.sp,color=Color.Gray)
                }
            }
        }
    }
    m.message?.let {AlertDialog(onDismissRequest=m::clearMessage,title={Text("Monstro Studio")},text={Text(it)},confirmButton={TextButton(onClick=m::clearMessage){Text("OK")}})}
}

@Composable
private fun Adjust(label:String,value:Float,range:ClosedFloatingPointRange<Float>,suffix:String="",change:(Float)->Unit){var local by remember(value){mutableStateOf(value.coerceIn(range))};Column {Text("$label  ${String.format(java.util.Locale.US,"%.2f",local)}$suffix",fontSize=12.sp,color=Color.LightGray);Slider(value=local,onValueChange={local=it},onValueChangeFinished={change(local)},valueRange=range,modifier=Modifier.height(30.dp))}}
@Composable
private fun Choices(values:List<String>,selected:String,change:(String)->Unit){LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(values){v->FilterChip(selected=v==selected,onClick={change(v)},label={Text(v,fontSize=11.sp)})}}}
@Composable
private fun Timing(start:Long,end:Long,total:Long,change:(Long,Long)->Unit){
    Adjust("Início",start/1000f,0f..maxOf(.01f,(total-1)/1000f),"s"){change((it*1000).toLong().coerceAtMost(end-1),end)}
    Adjust("Fim",end/1000f,.001f..maxOf(.001f,total/1000f),"s"){change(start,(it*1000).toLong().coerceAtLeast(start+1))}
}
@UnstableApi @Composable
private fun VideoInspector(m:EditorModel,importVideo:()->Unit){OutlinedButton(onClick=importVideo,modifier=Modifier.fillMaxWidth()){Text("+ Adicionar vídeo")};val clip=m.current ?: return
    Text(clip.name,maxLines=1,fontWeight=FontWeight.Bold)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Switch(checked=m.compatibilityPreview,onCheckedChange={m.toggleCompatibilityPreview()})
        Spacer(Modifier.width(8.dp))
        Column{
            Text("Modo compatibilidade",fontWeight=FontWeight.Bold)
            Text(if(m.compatibilityPreview)"Ativado · efeitos ocultos na prévia" else "Desativado · efeitos visíveis no vídeo",color=Color.Gray,fontSize=11.sp)
        }
    }
    Row(verticalAlignment=Alignment.CenterVertically){Text("Ferramentas do clipe",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick=m::removeNearestMarker){Text("Remover marcador")}}
    LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
        item{AssistChip(onClick=m::split,label={Text("✂ Dividir")})}
        item{AssistChip(onClick=m::extractCurrentAudio,label={Text("♪ Extrair áudio")})}
        item{AssistChip(onClick=m::toggleMirror,label={Text(if(clip.mirror)"⇆ Espelhado" else "⇆ Espelhar")})}
        item{AssistChip(onClick={m.move(-1)},label={Text("← Mover")})}
        item{AssistChip(onClick={m.move(1)},label={Text("Mover →")})}
        item{AssistChip(onClick=m::remove,label={Text("Excluir")})}
    }
    Adjust("Volume do clipe",clip.volume,0f..2f,"×",m::setClipVolume)
    Timing(clip.trim.start,clip.trim.end,clip.duration){a,b->m.edit(trim=TrimRange(a,b))}
    Choices(listOf("raw","neon","trap","dark","cinema"),clip.preset){m.edit(preset=it)}
    val motion=m.studio.motions[clip.id] ?: ClipMotion();val local=m.playhead-m.timelineOffset
    Adjust("Zoom",animated(motion.zoom,local,clip.chaos.zoom),.5f..3f,"×"){if(motion.zoom.isEmpty())m.edit(chaos=clip.chaos.copy(zoom=it))else m.setMotion(motion.copy(zoom=putKey(motion.zoom,local,it)))}
    Adjust("Posição X",animated(motion.x,local,0f),-.5f..0.5f){m.setMotion(motion.copy(x=putKey(motion.x,local,it)))}
    Adjust("Posição Y",animated(motion.y,local,0f),-.5f..0.5f){m.setMotion(motion.copy(y=putKey(motion.y,local,it)))}
    Adjust("Rotação",animated(motion.rotation,local,0f),-180f..180f,"°"){m.setMotion(motion.copy(rotation=putKey(motion.rotation,local,it)))}
    Row{
        TextButton(onClick={m.setMotion(motion.copy(
            zoom=putKey(motion.zoom,local,animated(motion.zoom,local,clip.chaos.zoom)),
            x=putKey(motion.x,local,animated(motion.x,local,0f)),
            y=putKey(motion.y,local,animated(motion.y,local,0f)),
            rotation=putKey(motion.rotation,local,animated(motion.rotation,local,0f))
        ))}){Text("◇ Keyframe transform")}
        TextButton(onClick={m.setMotion(motion.copy(zoom=emptyList(),x=emptyList(),y=emptyList(),rotation=emptyList()))}){Text("Limpar")}
    }
    Text("Animação do clipe",fontWeight=FontWeight.Bold)
    Choices(listOf("Nenhuma","Entrada Pop","Saída Zoom","Combo Punch"),""){name->
        val d=m.speedMap(clip).outputDuration;val edge=minOf(650L,d/2)
        m.setMotion(when(name){
            "Entrada Pop"->motion.copy(zoom=listOf(KeyPoint(0,1.20f),KeyPoint(edge,1f)),y=listOf(KeyPoint(0,.10f),KeyPoint(edge,0f)),rotation=emptyList(),x=emptyList())
            "Saída Zoom"->motion.copy(zoom=listOf(KeyPoint((d-edge).coerceAtLeast(0),1f),KeyPoint(d,1.22f)),x=listOf(KeyPoint((d-edge).coerceAtLeast(0),0f),KeyPoint(d,.08f)),y=emptyList(),rotation=emptyList())
            "Combo Punch"->motion.copy(zoom=listOf(KeyPoint(0,1f),KeyPoint(d/2,1.14f),KeyPoint(d,1f)),rotation=listOf(KeyPoint(0,0f),KeyPoint(d/2,2.5f),KeyPoint(d,0f)),x=emptyList(),y=emptyList())
            else->motion.copy(zoom=emptyList(),x=emptyList(),y=emptyList(),rotation=emptyList())
        })
    }
    Text("Velocity · curva de velocidade",fontWeight=FontWeight.Bold)
    Choices(listOf("Normal","Montanha","Hero","Bullet"),""){name->val d=clip.trim.duration;m.setMotion(motion.copy(speed=when(name){"Montanha"->listOf(KeyPoint(0,.5f),KeyPoint(d/2,3f),KeyPoint(d,.5f));"Hero"->listOf(KeyPoint(0,2f),KeyPoint(d/3,.35f),KeyPoint(d*2/3,.35f),KeyPoint(d,2f));"Bullet"->listOf(KeyPoint(0,1f),KeyPoint(d/3,4f),KeyPoint(d/2,.25f),KeyPoint(d,1f));else->emptyList()}))}
    Canvas(Modifier.fillMaxWidth().height(64.dp).background(Color(0xff15131e),RoundedCornerShape(8.dp))){
        var previous:Offset?=null
        for(i in 0..100){val value=animated(motion.speed,clip.trim.duration*i/100,1f);val point=Offset(size.width*i/100,size.height*(1-(value-.25f)/3.75f));previous?.let {drawLine(Color(0xffbf79ff),it,point,3f)};previous=point}
        motion.speed.forEach {drawCircle(Color.White,4f,Offset(size.width*it.time/clip.trim.duration,size.height*(1-(it.value-.25f)/3.75f)))}
    }
    var speed by remember(clip.id){mutableStateOf(1f)}
    Adjust("Velocidade",speed,.25f..4f,"×"){speed=it}
    Row{TextButton(onClick={m.setMotion(motion.copy(speed=listOf(KeyPoint(0,speed))))}){Text("Constante")};TextButton(onClick={m.setMotion(motion.copy(speed=putKey(motion.speed,m.player.currentPosition,speed)))}){Text("◇ Ponto na curva")}}
    if(motion.speed.isNotEmpty())Text(motion.speed.joinToString("  →  "){"${timeLabel(it.time)} ${String.format("%.1f",it.value)}×"},fontSize=10.sp)
    LazyRow {items(ChaosFx.values().toList()){fx->FilterChip(selected=clip.chaos.has(fx),onClick={m.toggleFx(fx)},label={Text(fx.label,fontSize=10.sp)})}}
    Row(verticalAlignment=Alignment.CenterVertically){Switch(checked=clip.chaos.has(ChaosFx.MOTION_BLUR),onCheckedChange={m.toggleFx(ChaosFx.MOTION_BLUR)});Text("Motion Blur")}
    Row(verticalAlignment=Alignment.CenterVertically){Switch(checked=!m.mute,onCheckedChange={m.toggleMute()});Text("Áudio original")}
}
@UnstableApi @Composable
private fun AudioInspector(m:EditorModel,import:()->Unit,recordVoice:()->Unit){
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
        OutlinedButton(onClick=import,enabled=!m.voiceoverRecording){Text("+ Importar áudio")}
        Button(onClick={if(m.voiceoverRecording)m.stopVoiceover()else recordVoice()},enabled=!m.busy){Text(if(m.voiceoverRecording)"■ Parar dublagem" else "● Gravar dublagem")}
    }
    if(m.voiceoverStatus.isNotBlank())Text(m.voiceoverStatus,fontSize=10.sp,color=if(m.voiceoverRecording)MaterialTheme.colorScheme.primary else Color.Gray)
    val layer=m.studio.audio.find {it.id==m.focusedId} ?: return
    fun update(next:AudioLayer){m.updateStudio(m.studio.copy(audio=m.studio.audio.map {if(it.id==layer.id)next else it}),false)}
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
        TextButton(onClick={m.autoCaptionAudio(layer.id)}){Text("Legendar áudio")}
        TextButton(onClick={m.autoBeatsAudio(layer.id)}){Text("Auto-Beats")}
    }
    if(m.beatStatus.isNotBlank() && !m.beatBusy)Text(m.beatStatus,fontSize=10.sp,color=Color.Gray)
    Text(layer.name)
    Adjust("Volume",layer.volume,0f..1f){update(layer.copy(volume=it))}
    Text("Voz / pitch",fontWeight=FontWeight.Bold)
    Choices(listOf("Normal","Esquilo","Monstro"),when{layer.pitch>1.2f->"Esquilo";layer.pitch<.85f->"Monstro";else->"Normal"}){name->update(layer.copy(pitch=when(name){"Esquilo"->1.45f;"Monstro"->.72f;else->1f}))}
    val clipLength=(layer.trimEnd-layer.trimStart).coerceAtLeast(1)
    val maxFade=minOf(5f,clipLength/2000f).coerceAtLeast(.1f)
    Adjust("Fade in",layer.fadeIn/1000f,0f..maxFade,"s"){update(layer.copy(fadeIn=(it*1000).toLong()))}
    Adjust("Fade out",layer.fadeOut/1000f,0f..maxFade,"s"){update(layer.copy(fadeOut=(it*1000).toLong()))}
    Adjust("Posição na timeline",layer.start/1000f,0f..maxOf(.01f,m.totalDuration/1000f),"s"){update(layer.copy(start=(it*1000).toLong()))}
    Timing(layer.trimStart,layer.trimEnd,layer.duration){a,b->update(layer.copy(trimStart=a,trimEnd=b))}
    TextButton(onClick={m.updateStudio(m.studio.copy(audio=m.studio.audio-layer),false)}){Text("Excluir áudio")}
}
@UnstableApi @Composable
private fun TextInspector(m:EditorModel){OutlinedButton(onClick=m::addText){Text("+ Adicionar texto")};val layer=m.studio.texts.find {it.id==m.focusedId} ?: return
    var text by remember(layer.id){mutableStateOf(layer.text)}
    OutlinedTextField(value=text,onValueChange={text=it.take(5000)},label={Text("Texto")},modifier=Modifier.fillMaxWidth())
    TextButton(onClick={m.updateStudio(m.studio.copy(texts=m.studio.texts.map {if(it.id==layer.id)it.copy(text=text)else it}),false)}){Text("Aplicar texto")}
    Text("Narração IA",fontWeight=FontWeight.Bold)
    var voice by remember(layer.id){mutableStateOf("Kore")}
    var voiceStyle by remember(layer.id){mutableStateOf("Natural")}
    Choices(listOf("Kore","Puck","Sulafat","Orus","Zephyr"),voice){voice=it}
    Choices(listOf("Natural","Animado","Calmo","Narrador"),voiceStyle){voiceStyle=it}
    OutlinedButton(onClick={m.generateNarration(text,voice,voiceStyle)},enabled=text.isNotBlank()&&!m.busy,modifier=Modifier.fillMaxWidth()){Text("♪ Gerar narração com Gemini")}
    if(m.ttsStatus.isNotBlank())Text(m.ttsStatus,fontSize=10.sp,color=Color.Gray)
    Text("Estilos rápidos",fontWeight=FontWeight.Bold)
    Choices(listOf("Clean","Neon","3D","Trap"),""){preset->
        val next=when(preset){
            "Neon"->layer.style.copy(font="sans-serif-condensed",color=0xffc250ff.toInt(),stroke=.004f,shadow=.006f,glow=.035f,animation="Pop")
            "3D"->layer.style.copy(font="sans-serif",color=0xffffffff.toInt(),stroke=.008f,shadow=.025f,glow=0f,animation="Pop")
            "Trap"->layer.style.copy(font="sans-serif-condensed",color=0xffff2355.toInt(),stroke=.007f,shadow=.012f,glow=.018f,animation="Digitar")
            else->layer.style.copy(font="sans-serif",color=0xffffffff.toInt(),stroke=.003f,shadow=.006f,glow=0f,animation="Fade")
        }
        m.updateStudio(m.studio.copy(texts=m.studio.texts.map {if(it.id==layer.id)it.copy(style=next)else it}),false)
    }
    Timing(layer.start,layer.end,m.totalDuration){a,b->m.updateStudio(m.studio.copy(texts=m.studio.texts.map {if(it.id==layer.id)it.copy(start=a,end=b)else it}),false)}
    StyleInspector(layer.style,m.playhead-layer.start){style->m.updateStudio(m.studio.copy(texts=m.studio.texts.map {if(it.id==layer.id)it.copy(style=style)else it}),false)}
    TextButton(onClick={m.updateStudio(m.studio.copy(texts=m.studio.texts-layer),false)}){Text("Excluir texto")}
}
@UnstableApi @Composable
private fun LayerInspector(m:EditorModel,importVideo:()->Unit,importImage:()->Unit,importAudio:()->Unit){
    Text("Mídia e camadas do projeto",fontWeight=FontWeight.Bold)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
        item{OutlinedButton(onClick=importVideo){Text("+ Vídeo")}}
        item{OutlinedButton(onClick=importImage){Text("+ Imagem")}}
        item{OutlinedButton(onClick=importAudio){Text("+ Áudio")}}
        item{OutlinedButton(onClick=m::addText){Text("+ Texto")}}
    }
    val selectedImage=m.studio.images.find {it.id==m.focusedId}
    if(selectedImage!=null){
        val local=(m.playhead-selectedImage.start).coerceAtLeast(0)
        fun update(next:ImageLayer){m.updateStudio(m.studio.copy(images=m.studio.images.map {if(it.id==selectedImage.id)next else it}),false)}
        Text(selectedImage.name,fontWeight=FontWeight.Bold)
        Adjust("Posição X",animated(selectedImage.xKeys,local,selectedImage.x),0f..1f){v->update(if(selectedImage.xKeys.isEmpty())selectedImage.copy(x=v)else selectedImage.copy(xKeys=putKey(selectedImage.xKeys,local,v)))}
        Adjust("Posição Y",animated(selectedImage.yKeys,local,selectedImage.y),0f..1f){v->update(if(selectedImage.yKeys.isEmpty())selectedImage.copy(y=v)else selectedImage.copy(yKeys=putKey(selectedImage.yKeys,local,v)))}
        Adjust("Escala",animated(selectedImage.scaleKeys,local,selectedImage.scale),.05f..1.5f,"×"){v->update(if(selectedImage.scaleKeys.isEmpty())selectedImage.copy(scale=v)else selectedImage.copy(scaleKeys=putKey(selectedImage.scaleKeys,local,v)))}
        Adjust("Rotação",animated(selectedImage.rotationKeys,local,selectedImage.rotation),-180f..180f,"°"){v->update(if(selectedImage.rotationKeys.isEmpty())selectedImage.copy(rotation=v)else selectedImage.copy(rotationKeys=putKey(selectedImage.rotationKeys,local,v)))}
        Adjust("Opacidade",animated(selectedImage.opacityKeys,local,selectedImage.opacity),0f..1f){v->update(if(selectedImage.opacityKeys.isEmpty())selectedImage.copy(opacity=v)else selectedImage.copy(opacityKeys=putKey(selectedImage.opacityKeys,local,v)))}
        Timing(selectedImage.start,selectedImage.end,m.totalDuration){a,b->update(selectedImage.copy(start=a,end=b))}
        Row{
            TextButton(onClick={update(selectedImage.copy(
                xKeys=putKey(selectedImage.xKeys,local,animated(selectedImage.xKeys,local,selectedImage.x)),
                yKeys=putKey(selectedImage.yKeys,local,animated(selectedImage.yKeys,local,selectedImage.y)),
                scaleKeys=putKey(selectedImage.scaleKeys,local,animated(selectedImage.scaleKeys,local,selectedImage.scale)),
                rotationKeys=putKey(selectedImage.rotationKeys,local,animated(selectedImage.rotationKeys,local,selectedImage.rotation)),
                opacityKeys=putKey(selectedImage.opacityKeys,local,animated(selectedImage.opacityKeys,local,selectedImage.opacity))
            ))}){Text("◇ Keyframe")}
            TextButton(onClick={update(selectedImage.copy(xKeys=emptyList(),yKeys=emptyList(),scaleKeys=emptyList(),rotationKeys=emptyList(),opacityKeys=emptyList()))}){Text("Limpar")}
        }
        TextButton(onClick={m.updateStudio(m.studio.copy(images=m.studio.images-selectedImage),false)}){Text("Excluir imagem")}
        HorizontalDivider()
    }
    Text("${m.clips.size} vídeo(s) · ${m.studio.images.size} imagem(ns) · ${m.studio.audio.size} áudio(s) · ${m.studio.texts.size} texto(s) · ${m.lyrics?.cues?.size ?: 0} legenda(s) · ${m.studio.fx.size} FX",fontSize=10.sp,color=Color.Gray)
    m.clips.take(12).forEachIndexed {index,clip->
        OutlinedButton(onClick={m.select(index);m.focus("Vídeo")},modifier=Modifier.fillMaxWidth()){Text("VÍDEO  ${index+1} · ${clip.name}",maxLines=1)}
    }
    m.studio.images.take(12).forEach {layer->OutlinedButton(onClick={m.focus("Camada",layer.id);m.seekTimeline(layer.start)},modifier=Modifier.fillMaxWidth()){Text("IMAGEM  ${layer.name}",maxLines=1)}}
    m.studio.audio.take(8).forEach {layer->OutlinedButton(onClick={m.focus("Áudio",layer.id)},modifier=Modifier.fillMaxWidth()){Text("ÁUDIO  ${layer.name}",maxLines=1)}}
    m.studio.texts.take(8).forEach {layer->OutlinedButton(onClick={m.focus("Texto",layer.id)},modifier=Modifier.fillMaxWidth()){Text("TEXTO  ${layer.text}",maxLines=1)}}
    m.studio.fx.take(8).forEach {layer->OutlinedButton(onClick={m.focus("FX",layer.id)},modifier=Modifier.fillMaxWidth()){Text("FX  ${FxCatalog.get(layer.presetId)?.name ?: layer.presetId}",maxLines=1)}}
}
@UnstableApi @Composable
private fun FilterInspector(m:EditorModel){
    val clip=m.current ?: run {Text("Importe um vídeo para usar filtros.");return}
    Text("Filtros do clipe",fontWeight=FontWeight.Bold)
    Text("Aplicação por GPU e visível também no MP4.",fontSize=10.sp,color=Color.Gray)
    val names=mapOf("Original" to "raw","Neon" to "neon","Trap" to "trap","Dark" to "dark","Cinema" to "cinema")
    Choices(names.keys.toList(),names.entries.firstOrNull {it.value==clip.preset}?.key ?: "Original"){label->m.edit(preset=names[label] ?: "raw")}
}

@UnstableApi @Composable
private fun RatioInspector(m:EditorModel){
    Text("Canvas e proporção",fontWeight=FontWeight.Bold)
    Choices(listOf("9:16","16:9","1:1","4:5"),m.canvasRatio,m::selectCanvasRatio)
    Text("Fundo",fontWeight=FontWeight.Bold)
    Choices(listOf("Desfoque","Cor sólida","Padrão"),when(m.canvasBackground){"solid"->"Cor sólida";"pattern"->"Padrão";else->"Desfoque"}){m.selectCanvasBackground(when(it){"Cor sólida"->"solid";"Padrão"->"pattern";else->"blur"})}
    Text("Saída atual: ${m.exportFormat.width} × ${m.exportFormat.height}",fontSize=11.sp,color=Color.Gray)
}

@UnstableApi @Composable
private fun AdjustInspector(m:EditorModel){
    val clip=m.current ?: run {Text("Selecione um vídeo para ajustar a cor.");return}
    val a=m.studio.adjustments[clip.id] ?: ClipAdjust()
    fun update(next:ClipAdjust){m.updateStudio(m.studio.copy(adjustments=m.studio.adjustments+(clip.id to next)))}
    Text("Ajustes de cor",fontWeight=FontWeight.Bold)
    Adjust("Brilho",a.brightness,-1f..1f){update(a.copy(brightness=it))}
    Adjust("Contraste",a.contrast,-1f..1f){update(a.copy(contrast=it))}
    Adjust("Saturação",a.saturation,-100f..100f){update(a.copy(saturation=it))}
    Adjust("Matiz",a.hue,-180f..180f,"°"){update(a.copy(hue=it))}
    Adjust("Luminosidade",a.lightness,-100f..100f){update(a.copy(lightness=it))}
    Adjust("Temperatura",a.temperature,-1f..1f){update(a.copy(temperature=it))}
    OutlinedButton(onClick={update(ClipAdjust())},modifier=Modifier.fillMaxWidth()){Text("Redefinir ajustes")}
}

@UnstableApi @Composable
private fun CaptionInspector(m:EditorModel,import:()->Unit){
    Row {OutlinedButton(onClick={m.addManualCaption()}){Text("+ Legenda")};Spacer(Modifier.width(6.dp));OutlinedButton(onClick=import){Text("+ SRT")}}
    Text("Motor de legenda",fontWeight=FontWeight.Bold)
    Choices(listOf("Gemini IA","Offline"),if(m.captionEngine=="gemini")"Gemini IA" else "Offline"){m.selectCaptionEngine(if(it=="Gemini IA")"gemini" else "offline")}
    if(m.captionEngine=="gemini"){
        Text(if(m.geminiReady)"Gemini IA conectado · usa internet" else "Gemini IA aguardando configuração do Firebase",fontSize=10.sp,color=if(m.geminiReady)Color.LightGray else Color(0xffffb86b))
        OutlinedButton(onClick=m::autoCaption,modifier=Modifier.fillMaxWidth()){Text("Legendar com Gemini IA")}
    } else {
        OutlinedButton(onClick=if(m.modelReady)m::autoCaption else m::installSpeech,modifier=Modifier.fillMaxWidth()){
            Text(if(m.modelReady)"Legendar Offline" else "Baixar português Offline · 31 MB",fontSize=11.sp)
        }
    }
    if(m.speechStatus.isNotBlank())Text(m.speechStatus,fontSize=11.sp)
    Text("Gemini IA: leitura avançada do áudio. Offline: Vosk no aparelho. Word Sync usa os tempos por palavra gerados pelo motor.",fontSize=10.sp,color=Color.Gray)
    val index=m.focusedId.toIntOrNull() ?: m.lyrics?.cues?.indexOf(m.lyrics?.at(m.playhead)) ?: -1
    val cue=m.lyrics?.cues?.getOrNull(index)
    if(cue!=null){var text by remember(index,cue.text){mutableStateOf(cue.text)};OutlinedTextField(value=text,onValueChange={text=it.take(2000)},label={Text("Revisar frase")},modifier=Modifier.fillMaxWidth());TextButton(onClick={m.updateCue(index,text,cue.startMs,cue.endMs)}){Text("Salvar frase")};Timing(cue.startMs,cue.endMs,maxOf(m.totalDuration,cue.endMs)){a,b->m.updateCue(index,text,a,b)}}
    if(m.lyrics!=null){Text(if(cue==null)"Estilo geral" else "Estilo desta frase",fontWeight=FontWeight.Bold);val style=m.studio.captionStyles[index] ?: m.studio.captionStyle
        StyleInspector(style,m.playhead-(cue?.startMs ?: 0)){s->m.updateStudio(if(cue==null)m.studio.copy(captionStyle=s)else m.studio.copy(captionStyles=m.studio.captionStyles+(index to s)),false)}
        TextButton(onClick={m.updateStudio(m.studio.copy(captionStyle=style,captionStyles=emptyMap()),false)}){Text("Aplicar estilo a todas")}
        Row(verticalAlignment=Alignment.CenterVertically){Switch(checked=m.simpleLyrics,onCheckedChange={m.toggleSimpleLyrics()});Text("Legenda leve (sem glow)")}
        TextButton(onClick=m::removeLyrics){Text("Remover legendas")}
    }
}
@Composable
private fun StyleInspector(s:TextStyle,time:Long,change:(TextStyle)->Unit){
    Choices(listOf("sans-serif-condensed","sans-serif","serif","monospace"),s.font){change(s.copy(font=it))}
    Adjust("Tamanho",s.size,.02f..0.25f){change(s.copy(size=it,scaleKeys=if(s.scaleKeys.isEmpty())emptyList()else putKey(s.scaleKeys,time.coerceAtLeast(0),it)))}
    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){items(listOf(0xffc250ff.toInt(),0xffff2355.toInt(),0xffffffff.toInt(),0xffffd23f.toInt(),0xff32f5ca.toInt(),0xff000000.toInt())){color->Box(Modifier.size(42.dp).background(Color(color),RoundedCornerShape(20.dp)).border(if(s.color==color)3.dp else 1.dp,Color.LightGray,RoundedCornerShape(20.dp)).clickable {change(s.copy(color=color))})}}
    var hex by remember(s.color){mutableStateOf(String.format("%06X",s.color and 0xffffff))}
    Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(value=hex,onValueChange={hex=it.removePrefix("#").take(6)},label={Text("Cor HEX")},singleLine=true,modifier=Modifier.weight(1f));TextButton(onClick={hex.toLongOrNull(16)?.takeIf {hex.length==6}?.let {change(s.copy(color=(it or 0xff000000L).toInt()))}}){Text("Aplicar")}}
    Adjust("Stroke",s.stroke,0f..0.025f){change(s.copy(stroke=it))};Adjust("Sombra",s.shadow,0f..0.04f){change(s.copy(shadow=it))};Adjust("Glow",s.glow,0f..0.06f){change(s.copy(glow=it))}
    Adjust("Horizontal",s.x,.05f..0.95f){change(s.copy(x=it,xKeys=if(s.xKeys.isEmpty())emptyList()else putKey(s.xKeys,time.coerceAtLeast(0),it)))};Adjust("Vertical",s.y,.05f..0.95f){change(s.copy(y=it,yKeys=if(s.yKeys.isEmpty())emptyList()else putKey(s.yKeys,time.coerceAtLeast(0),it)))};Adjust("Opacidade",s.opacity,.1f..1f){change(s.copy(opacity=it))}
    Choices(listOf("Nenhuma","Pop","Fade","Digitar"),s.animation){change(s.copy(animation=it))}
    Row{TextButton(onClick={change(s.copy(xKeys=putKey(s.xKeys,time.coerceAtLeast(0),s.x),yKeys=putKey(s.yKeys,time.coerceAtLeast(0),s.y),scaleKeys=putKey(s.scaleKeys,time.coerceAtLeast(0),s.size)))}){Text("◇ Keyframe posição / escala")};TextButton(onClick={change(s.copy(xKeys=emptyList(),yKeys=emptyList(),scaleKeys=emptyList()))}){Text("Limpar")}}
}
@UnstableApi @Composable
private fun FxInspector(m:EditorModel,library:()->Unit){OutlinedButton(onClick=library,modifier=Modifier.fillMaxWidth()){Text("+ Biblioteca · ${FxCatalog.all.size} FX")};val layer=m.studio.fx.find {it.id==m.focusedId} ?: return
    Text(FxCatalog.get(layer.presetId)?.name ?: "FX",fontWeight=FontWeight.Bold)
    fun update(next:FxLayer){m.updateStudio(m.studio.copy(fx=m.studio.fx.map {if(it.id==layer.id)next else it}))}
    Adjust("Intensidade",layer.intensity,0f..2f){update(layer.copy(intensity=it,keys=if(layer.keys.isEmpty())emptyList()else putKey(layer.keys,(m.playhead-layer.start).coerceAtLeast(0),it)))};Adjust("Velocidade",layer.speed,.1f..4f){update(layer.copy(speed=it))};if(FxCatalog.get(layer.presetId)?.engine in listOf(1,5,18))Adjust("Direção",layer.direction,0f..360f,"°"){update(layer.copy(direction=it))}
    Timing(layer.start,layer.end,m.totalDuration){a,b->update(layer.copy(start=a,end=b))}
    Row{TextButton(onClick={update(layer.copy(keys=putKey(layer.keys,(m.playhead-layer.start).coerceAtLeast(0),layer.intensity)))}){Text("◇ Keyframe intensidade")};TextButton(onClick={update(layer.copy(keys=emptyList()))}){Text("Limpar")}}
    TextButton(onClick={m.updateStudio(m.studio.copy(fx=m.studio.fx-layer))}){Text("Excluir FX")}
}
@UnstableApi @Composable
private fun FxLibrary(m:EditorModel,close:()->Unit){var query by remember{mutableStateOf("")};var category by remember{mutableStateOf("Todos")}
    val results=remember(query,category,m.studio.favorites,m.studio.recent){FxCatalog.search(query,category.takeIf {it !in listOf("Todos","Favoritos","Recentes")}).filter {when(category){"Favoritos"->it.id in m.studio.favorites;"Recentes"->it.id in m.studio.recent;else->true}}.let {if(category=="Recentes")it.sortedBy {p->m.studio.recent.indexOf(p.id)}else it}}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize().systemBarsPadding(),color=Color(0xff101017)){Column(Modifier.padding(16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text("CHAOS LIBRARY",fontWeight=FontWeight.Black,modifier=Modifier.weight(1f));TextButton(onClick=close){Text("Fechar")}}
        Text("20 motores · 1.000 receitas animadas",fontSize=12.sp,color=Color.Gray)
        OutlinedTextField(value=query,onValueChange={query=it},label={Text("Pesquisar FX")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Choices(listOf("Todos","Favoritos","Recentes")+FxCatalog.categories,category){category=it}
        Text("${results.size} resultados",fontSize=11.sp,color=Color.Gray)
        LazyColumn {items(results,key={it.id}){preset->Row(Modifier.fillMaxWidth().clickable {m.addFx(preset);close()}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(Color.hsv(preset.engine*18f,.65f,.65f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Text("FX",fontWeight=FontWeight.Black)};Column(Modifier.weight(1f).padding(start=12.dp)){Text(preset.name,fontSize=13.sp);Text(preset.category,fontSize=10.sp,color=Color.Gray)};TextButton(onClick={m.favorite(preset.id)}){Text(if(preset.id in m.studio.favorites)"★" else "☆",fontSize=22.sp)}}}}
    }}}
}
