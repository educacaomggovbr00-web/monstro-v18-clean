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
    val srt=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),m::importLyrics)
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4"),m::saveOutput)
    var library by remember {mutableStateOf(false)};var exportDialog by remember {mutableStateOf(false)}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,m){val listener=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP)m.pauseAll()};owner.lifecycle.addObserver(listener);onDispose{owner.lifecycle.removeObserver(listener)}}
    LaunchedEffect(m){while(true){m.tick();delay(40)}}
    Column(Modifier.fillMaxSize().background(Color(0xff09090f)).systemBarsPadding()){
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("MONSTRO",fontWeight=FontWeight.Black,letterSpacing=2.sp,fontSize=18.sp);Text("V18  /  STUDIO",color=MaterialTheme.colorScheme.primary,fontSize=9.sp,letterSpacing=2.sp)}
            TextButton(onClick={videos.launch(arrayOf("video/*"))},enabled=!m.busy){Text("+ Mídia")}
            Button(onClick={exportDialog=true},enabled=m.clips.isNotEmpty()&&!m.busy,contentPadding=PaddingValues(horizontal=12.dp)){Text("Exportar")}
        }
        Box(Modifier.fillMaxWidth().heightIn(min=140.dp,max=320.dp).weight(1.65f).clipToBounds().background(Color.Black),contentAlignment=Alignment.Center){
            if(m.current==null)Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Seu próximo edit começa aqui",fontWeight=FontWeight.Bold);TextButton(onClick={videos.launch(arrayOf("video/*"))}){Text("+ Importar vídeos")}}
            else Box(Modifier.fillMaxHeight().aspectRatio(if(m.vertical)9f/16 else 16f/9)){
                key(m.player){AndroidView(factory={EditorPlayerView(it).apply {useController=false;resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT}},update={it.bind(m.player,!m.compatibilityPreview)},modifier=Modifier.fillMaxSize())}
                AndroidView(factory={StudioPreview(it)},update={it.model=m;it.invalidate()},modifier=Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically){
            Text(timeLabel(m.playhead),color=Color.White,fontSize=12.sp);Text(" / ${timeLabel(m.totalDuration)}",color=Color.Gray,fontSize=11.sp,modifier=Modifier.weight(1f))
            TextButton(onClick={m.seekTimeline(m.playhead-100)},enabled=!m.busy){Text("−0,1s")}
            TextButton(onClick={if(m.player.isPlaying)m.pauseAll()else {if(m.player.playbackState==androidx.media3.common.Player.STATE_ENDED)m.seekTimeline(0);m.player.play()}},enabled=m.current!=null&&!m.busy){Text(if(m.player.isPlaying)"Ⅱ" else "▶",fontSize=20.sp)}
            TextButton(onClick={m.seekTimeline(m.playhead+100)},enabled=!m.busy){Text("+0,1s")}
        }
        AndroidView(factory={StudioTimeline(it)},update={it.model=m;it.invalidate()},modifier=Modifier.fillMaxWidth().height(191.dp))
        Text("Arraste a régua para buscar · dois dedos para ampliar",color=Color.Gray,fontSize=9.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=2.dp))
        LazyRow(Modifier.fillMaxWidth().background(Color(0xff18151f)),horizontalArrangement=Arrangement.spacedBy(2.dp)){
            items(listOf("Vídeo","Áudio","Texto","Legenda","FX")){kind->TextButton(onClick={m.focus(kind)},enabled=!m.busy){Text(kind,color=if(m.inspector==kind)MaterialTheme.colorScheme.primary else Color.LightGray,fontWeight=FontWeight.Bold)}}
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
                } else {
                    LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                    Text(if(m.exporting)"Renderizando ${m.progress?.let {"$it%"} ?: "…"}" else "Preparando…")
                    TextButton(onClick=m::cancelExport){Text("Cancelar")}
                }
            }
            else when(m.inspector){
                "Vídeo"->VideoInspector(m){videos.launch(arrayOf("video/*"))}
                "Áudio"->AudioInspector(m){audio.launch(arrayOf("audio/*"))}
                "Texto"->TextInspector(m)
                "Legenda"->CaptionInspector(m){srt.launch(arrayOf("*/*"))}
                "FX"->FxInspector(m){library=true}
            }
            if(m.output!=null)OutlinedButton(onClick={save.launch("MONSTRO_Studio.mp4")},enabled=!m.busy,modifier=Modifier.fillMaxWidth()){Text("Salvar último MP4")}
        }
    }
    if(library)FxLibrary(m){library=false}
    if(exportDialog)AlertDialog(onDismissRequest={exportDialog=false},title={Text("Exportar edit")},text={Column {
        Text("Formato");Row{FilterChip(selected=m.vertical,onClick={m.setExportFormat(true)},label={Text("9:16")});Spacer(Modifier.width(8.dp));FilterChip(selected=!m.vertical,onClick={m.setExportFormat(false)},label={Text("16:9")})}
        Text("Preenchimento com fundo desfocado")
        Row(verticalAlignment=Alignment.CenterVertically){Switch(checked=m.safeMode,onCheckedChange={m.toggleSafeMode()});Text(if(m.safeMode)"Leve · 540p / 30 fps" else "Alta qualidade · 1080p / 30 fps")}
    }},confirmButton={Button(onClick={exportDialog=false;m.export()}){Text("Gerar MP4")}},dismissButton={TextButton(onClick={exportDialog=false}){Text("Voltar")}})
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
    Row{TextButton(onClick=m::split){Text("Dividir")};TextButton(onClick={m.move(-1)}){Text("←")};TextButton(onClick={m.move(1)}){Text("→")};TextButton(onClick=m::remove){Text("Excluir")}}
    Timing(clip.trim.start,clip.trim.end,clip.duration){a,b->m.edit(trim=TrimRange(a,b))}
    Choices(listOf("raw","neon","trap","dark","cinema"),clip.preset){m.edit(preset=it)}
    val motion=m.studio.motions[clip.id] ?: ClipMotion();val local=m.playhead-m.timelineOffset
    Adjust("Zoom",animated(motion.zoom,local,clip.chaos.zoom),1f..3f,"×"){if(motion.zoom.isEmpty())m.edit(chaos=clip.chaos.copy(zoom=it))else m.setMotion(motion.copy(zoom=putKey(motion.zoom,local,it)))}
    Row{TextButton(onClick={m.setMotion(motion.copy(zoom=putKey(motion.zoom,local,animated(motion.zoom,local,clip.chaos.zoom))))}){Text("◇ Keyframe zoom")};TextButton(onClick={m.setMotion(motion.copy(zoom=emptyList()))}){Text("Limpar")}}
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
private fun AudioInspector(m:EditorModel,import:()->Unit){OutlinedButton(onClick=import){Text("+ Importar áudio")};val layer=m.studio.audio.find {it.id==m.focusedId} ?: return
    TextButton(onClick={m.autoCaptionAudio(layer.id)}){Text("Legendar este áudio")}
    Text(layer.name);Adjust("Volume",layer.volume,0f..1f){v->m.updateStudio(m.studio.copy(audio=m.studio.audio.map {if(it.id==layer.id)it.copy(volume=v)else it}),false)}
    Adjust("Posição na timeline",layer.start/1000f,0f..maxOf(.01f,m.totalDuration/1000f),"s"){v->m.updateStudio(m.studio.copy(audio=m.studio.audio.map {if(it.id==layer.id)it.copy(start=(v*1000).toLong())else it}),false)}
    Timing(layer.trimStart,layer.trimEnd,layer.duration){a,b->m.updateStudio(m.studio.copy(audio=m.studio.audio.map {if(it.id==layer.id)it.copy(trimStart=a,trimEnd=b)else it}),false)}
    TextButton(onClick={m.updateStudio(m.studio.copy(audio=m.studio.audio-layer),false)}){Text("Excluir áudio")}
}
@UnstableApi @Composable
private fun TextInspector(m:EditorModel){OutlinedButton(onClick=m::addText){Text("+ Adicionar texto")};val layer=m.studio.texts.find {it.id==m.focusedId} ?: return
    var text by remember(layer.id){mutableStateOf(layer.text)}
    OutlinedTextField(value=text,onValueChange={text=it.take(2000)},label={Text("Texto")},modifier=Modifier.fillMaxWidth())
    TextButton(onClick={m.updateStudio(m.studio.copy(texts=m.studio.texts.map {if(it.id==layer.id)it.copy(text=text)else it}),false)}){Text("Aplicar texto")}
    Timing(layer.start,layer.end,m.totalDuration){a,b->m.updateStudio(m.studio.copy(texts=m.studio.texts.map {if(it.id==layer.id)it.copy(start=a,end=b)else it}),false)}
    StyleInspector(layer.style,m.playhead-layer.start){style->m.updateStudio(m.studio.copy(texts=m.studio.texts.map {if(it.id==layer.id)it.copy(style=style)else it}),false)}
    TextButton(onClick={m.updateStudio(m.studio.copy(texts=m.studio.texts-layer),false)}){Text("Excluir texto")}
}
@UnstableApi @Composable
private fun CaptionInspector(m:EditorModel,import:()->Unit){
    Row {OutlinedButton(onClick={m.addManualCaption()}){Text("+ Legenda")};Spacer(Modifier.width(6.dp));OutlinedButton(onClick=import){Text("+ SRT")};Spacer(Modifier.width(6.dp));OutlinedButton(onClick=if(m.modelReady)m::autoCaption else m::installSpeech){Text(if(m.modelReady)"Legendar fala" else "Baixar português · 31 MB",fontSize=11.sp)}}
    if(m.speechStatus.isNotBlank())Text(m.speechStatus,fontSize=11.sp)
    Text("Automática: tempos por palavra. SRT: word-sync aproximado.",fontSize=10.sp,color=Color.Gray)
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
