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

private data class StudioTool(val glyph:String,val label:String,val action:()->Unit)

@Composable
private fun ToolCell(tool:StudioTool,wide:Boolean=false){
    Column(
        Modifier.width(if(wide)92.dp else 76.dp).height(76.dp).clickable(onClick=tool.action).padding(horizontal=4.dp,vertical=7.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(5.dp)
    ){
        Text(tool.glyph,fontSize=25.sp,color=Color(0xffe4e4e4))
        Text(tool.label,fontSize=10.sp,color=Color(0xffe8e8e8),maxLines=2)
    }
}

@Composable
private fun ToolStrip(tools:List<StudioTool>,back:(()->Unit)?=null){
    Surface(color=Color(0xff242424),modifier=Modifier.fillMaxWidth().height(86.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            if(back!=null){
                Box(
                    Modifier.padding(start=9.dp,end=6.dp).size(width=48.dp,height=68.dp)
                        .background(Color(0xff3b3b3b),RoundedCornerShape(5.dp)).clickable(onClick=back),
                    contentAlignment=Alignment.Center
                ){Text("‹",fontSize=36.sp,color=Color.LightGray)}
            }
            LazyRow(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically){
                items(tools){tool->ToolCell(tool)}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun StudioScreen(m:EditorModel,onBack:(()->Unit)?=null){
    val videos=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(),m::importVideos)
    val audio=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),m::importAudio)
    val image=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),m::importImage)
    val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted)m.startVoiceover() else m.showMessage("Permita acesso ao microfone para gravar dublagem.")
    }
    val srt=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),m::importLyrics)
    val fxPack=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),m::importFxPack)
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4"),m::saveOutput)

    var library by remember {mutableStateOf(false)}
    var exportDialog by remember {mutableStateOf(false)}
    var mediaDialog by remember {mutableStateOf(false)}
    var aiDialog by remember {mutableStateOf(false)}
    var fullscreen by remember {mutableStateOf(false)}
    var menuMode by remember {mutableStateOf("main")}
    var panel by remember {mutableStateOf<String?>(null)}

    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,m){
        val listener=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP)m.pauseAll()}
        owner.lifecycle.addObserver(listener)
        onDispose{owner.lifecycle.removeObserver(listener)}
    }
    LaunchedEffect(m){
        while(true){
            m.tick()
            delay(if(m.player.isPlaying || m.busy)33 else 120)
        }
    }

    fun openPanel(kind:String){
        m.focus(kind)
        panel=kind
    }

    val mainTools=listOf(
        StudioTool("✂","Editar"){m.focus("Vídeo");menuMode="clip"},
        StudioTool("♪","Áudio"){m.focus("Áudio");menuMode="audio"},
        StudioTool("T","Texto"){m.focus("Texto");menuMode="text"},
        StudioTool("✦","Efeitos"){m.focus("FX");menuMode="fx"},
        StudioTool("▣","Camada"){m.focus("Camada");menuMode="layer"},
        StudioTool("CC","Legendas"){m.focus("Legenda");menuMode="caption"},
        StudioTool("◌","Filtros"){openPanel("Filtros")},
        StudioTool("☷","Ajustar"){openPanel("Ajustes")},
        StudioTool("◒","Stickers"){image.launch(arrayOf("image/*"))},
        StudioTool("✧","Gerar mídia"){aiDialog=true},
        StudioTool("▭","Proporção"){openPanel("Proporção")}
    )

    val clipTools=listOf(
        StudioTool(if(m.mute)"🔇" else "🔊","Silenciar áudio"){m.toggleMute()},
        StudioTool("✂","Cortador de IA"){aiDialog=true},
        StudioTool("✂","Dividir"){m.split()},
        StudioTool("◖","Volume"){openPanel("Vídeo")},
        StudioTool("▣","Animações"){openPanel("Vídeo")},
        StudioTool("✦","Efeitos"){library=true},
        StudioTool("⌫","Excluir"){m.remove();menuMode="main"},
        StudioTool("◴","Velocidade"){openPanel("Vídeo")},
        StudioTool("♪","Extrair áudio"){m.extractCurrentAudio()},
        StudioTool("⇆","Espelhar"){m.toggleMirror()},
        StudioTool("◇","Transformar"){openPanel("Vídeo")},
        StudioTool("◆+","Keyframe"){m.addTimelineMarker()},
        StudioTool("◩","Filtros"){openPanel("Filtros")},
        StudioTool("☷","Ajustar"){openPanel("Ajustes")}
    )

    val audioTools=listOf(
        StudioTool("+","Importar"){audio.launch(arrayOf("audio/*"))},
        StudioTool("●","Gravar"){mic.launch(android.Manifest.permission.RECORD_AUDIO)},
        StudioTool("♬","Auto-Beats"){m.studio.audio.firstOrNull()?.let {m.autoBeatsAudio(it.id)} ?: m.showMessage("Importe ou selecione um áudio primeiro.")},
        StudioTool("CC","Legendar"){m.studio.audio.firstOrNull()?.let {m.autoCaptionAudio(it.id)} ?: m.showMessage("Importe ou selecione um áudio primeiro.")},
        StudioTool("◖","Volume / voz"){openPanel("Áudio")},
        StudioTool("T♪","Texto em áudio"){m.focus("Texto");panel="Texto"}
    )

    val textTools=listOf(
        StudioTool("A+","Adicionar texto"){m.addText();panel="Texto"},
        StudioTool("CC","Legendas autom."){m.autoCaption()},
        StudioTool("◒","Stickers"){image.launch(arrayOf("image/*"))},
        StudioTool("✎","Editar texto"){openPanel("Texto")},
        StudioTool("T♪","Texto em áudio"){openPanel("Texto")}
    )

    val captionTools=listOf(
        StudioTool("CC+","Inserir legendas"){m.addManualCaption();panel="Legenda"},
        StudioTool("⌗","Legendas autom."){m.autoCaption()},
        StudioTool("🌐","Traduzir IA"){openPanel("Legenda")},
        StudioTool("CC","Modelos"){openPanel("Legenda")},
        StudioTool("▱","Importar"){srt.launch(arrayOf("*/*"))}
    )

    val fxTools=listOf(
        StudioTool("✦","Efeitos"){library=true},
        StudioTool("↓","Importar FX"){fxPack.launch(arrayOf("application/json","text/plain","application/xml","text/xml","application/octet-stream"))},
        StudioTool("◇","IA sugerir"){aiDialog=true}
    )

    val layerTools=listOf(
        StudioTool("+","Adicionar camada"){image.launch(arrayOf("image/*"))},
        StudioTool("▣+","Adicionar vídeo"){videos.launch(arrayOf("video/*"))},
        StudioTool("▱","Editar camada"){openPanel("Camada")}
    )

    Box(Modifier.fillMaxSize().background(Color(0xff121212)).systemBarsPadding()){
        Column(Modifier.fillMaxSize()){
            // Minimal top bar from the reference layout.
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal=10.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                TextButton(
                    onClick={m.pauseAll();onBack?.invoke()},
                    contentPadding=PaddingValues(5.dp)
                ){Text("×",fontSize=38.sp,fontWeight=FontWeight.Light,color=Color(0xffdedede))}
                TextButton(onClick={library=true},contentPadding=PaddingValues(5.dp)){
                    Text("⌕",fontSize=38.sp,color=Color(0xffdedede))
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    color=Color(0xff2b2b2b),
                    shape=RoundedCornerShape(11.dp),
                    modifier=Modifier.clickable {exportDialog=true}
                ){
                    Text(
                        if(m.safeMode)"540p · ${m.exportFps}" else "1080p · ${m.exportFps}",
                        modifier=Modifier.padding(horizontal=14.dp,vertical=11.dp),
                        color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick={exportDialog=true},
                    enabled=m.clips.isNotEmpty()&&!m.busy,
                    colors=ButtonDefaults.buttonColors(containerColor=Color(0xff12cfe0),contentColor=Color(0xff101010)),
                    shape=RoundedCornerShape(10.dp),
                    contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp)
                ){Text("Exportar",fontWeight=FontWeight.Bold)}
            }

            // Large breathing room above preview, matching the supplied mobile reference.
            Spacer(Modifier.height(64.dp))

            Box(
                Modifier.fillMaxWidth().aspectRatio(m.exportFormat.aspect)
                    .clipToBounds().background(Color.Black),
                contentAlignment=Alignment.Center
            ){
                if(m.current==null){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        Text("Adicione um vídeo",color=Color.LightGray,fontWeight=FontWeight.Bold)
                        TextButton(onClick={mediaDialog=true}){Text("+ Mídia")}
                    }
                }else{
                    key(m.player,fullscreen){
                        AndroidView(
                            factory={EditorPlayerView(it).apply {useController=false;resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT}},
                            update={it.bind(m.player,true)},
                            modifier=Modifier.fillMaxSize()
                        )
                    }
                    AndroidView(
                        factory={StudioPreview(it)},
                        update={it.model=m;it.invalidate()},
                        modifier=Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.weight(.12f))

            // Playback / project controls.
            Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal=14.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                TextButton(onClick={fullscreen=true},enabled=m.current!=null,contentPadding=PaddingValues(4.dp)){Text("⛶",fontSize=24.sp,color=Color.LightGray)}
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick={
                        if(m.player.isPlaying)m.pauseAll()
                        else{
                            if(m.player.playbackState==androidx.media3.common.Player.STATE_ENDED)m.seekTimeline(0)
                            m.player.play()
                        }
                    },
                    enabled=m.current!=null&&!m.busy,
                    contentPadding=PaddingValues(4.dp)
                ){Text(if(m.player.isPlaying)"Ⅱ" else "▷",fontSize=30.sp,color=Color(0xffe8e8e8))}
                Spacer(Modifier.weight(1f))
                TextButton(onClick=m::addTimelineMarker,enabled=m.current!=null&&!m.busy,contentPadding=PaddingValues(4.dp)){Text("◇+",fontSize=22.sp,color=Color.LightGray)}
                TextButton(onClick=m::undo,enabled=m.canUndo&&!m.busy,contentPadding=PaddingValues(4.dp)){Text("↶",fontSize=25.sp,color=Color.LightGray)}
                TextButton(onClick=m::redo,enabled=m.canRedo&&!m.busy,contentPadding=PaddingValues(4.dp)){Text("↷",fontSize=25.sp,color=Color.LightGray)}
            }

            // Timeline: centered fixed playhead; the content scrolls underneath.
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xff1b1b1b))){
                AndroidView(
                    factory={ctx->StudioTimeline(ctx).also {view->
                        view.onAddMedia={mediaDialog=true}
                        view.onFocusChanged={kind,_->
                            menuMode=when(kind){
                                "Vídeo"->"clip";"Áudio"->"audio";"Texto"->"text";"Legenda"->"caption";"FX"->"fx";"Camada"->"layer";else->"main"
                            }
                        }
                    }},
                    update={view->
                        view.model=m
                        view.onAddMedia={mediaDialog=true}
                        view.onFocusChanged={kind,_->
                            menuMode=when(kind){
                                "Vídeo"->"clip";"Áudio"->"audio";"Texto"->"text";"Legenda"->"caption";"FX"->"fx";"Camada"->"layer";else->"main"
                            }
                        }
                        view.invalidate()
                    },
                    modifier=Modifier.fillMaxSize()
                )

                // MONSTRO AI quick action in the same ergonomic position as the floating editor action.
                Surface(
                    modifier=Modifier.align(Alignment.BottomStart).padding(start=18.dp,bottom=18.dp)
                        .size(52.dp).clickable(enabled=m.clips.isNotEmpty()&&!m.busy){aiDialog=true},
                    color=Color(0xff263239),
                    shape=RoundedCornerShape(26.dp),
                    shadowElevation=5.dp
                ){
                    Box(contentAlignment=Alignment.Center){Text("✦",fontSize=25.sp,color=Color(0xff13d5e5))}
                }
            }

            val activeTools=when(menuMode){
                "clip"->clipTools
                "audio"->audioTools
                "text"->textTools
                "caption"->captionTools
                "fx"->fxTools
                "layer"->layerTools
                else->mainTools
            }
            ToolStrip(activeTools,if(menuMode=="main")null else ({menuMode="main";panel=null}))
        }

        if(m.busy){
            Surface(
                modifier=Modifier.align(Alignment.BottomCenter).padding(horizontal=20.dp).padding(bottom=96.dp).fillMaxWidth(),
                color=Color(0xee242424),shape=RoundedCornerShape(16.dp),shadowElevation=8.dp
            ){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    val title=when{
                        m.speechBusy->"Analisando áudio"
                        m.translationBusy->"Traduzindo legendas"
                        m.ttsBusy->"Criando narração IA"
                        m.aiEditBusy->"IA Auto Edit"
                        m.beatBusy->"Auto-Beats"
                        m.exporting->"Exportando"
                        else->"Preparando"
                    }
                    Text(title,fontWeight=FontWeight.Bold)
                    LinearProgressIndicator(
                        progress=when{
                            m.speechProgress!=null->m.speechProgress!!/100f
                            m.translationBusy->m.translationProgress/100f
                            m.aiEditBusy->m.aiEditProgress/100f
                            m.beatBusy->m.beatProgress/100f
                            m.progress!=null->m.progress!!/100f
                            else->0f
                        },
                        modifier=Modifier.fillMaxWidth()
                    )
                    Text(
                        when{
                            m.speechBusy->m.speechStatus
                            m.translationBusy->m.translationStatus
                            m.ttsBusy->m.ttsStatus
                            m.aiEditBusy->m.aiEditStatus
                            m.beatBusy->m.beatStatus
                            else->m.progress?.let{"$it%"} ?: ""
                        },
                        color=Color.LightGray,fontSize=11.sp
                    )
                    TextButton(onClick={
                        when{
                            m.speechBusy->m.cancelSpeech()
                            m.translationBusy->m.cancelTranslation()
                            m.ttsBusy->m.cancelNarration()
                            m.aiEditBusy->m.cancelAiAutoEdit()
                            m.beatBusy->m.cancelAutoBeats()
                            m.exporting->m.cancelExport()
                        }
                    }){Text("Cancelar")}
                }
            }
        }
    }

    // Existing professional controls remain available, but now as a bottom sheet.
    panel?.let {kind->
        ModalBottomSheet(
            onDismissRequest={panel=null},
            containerColor=Color(0xff202020),
            contentColor=Color.White,
            dragHandle={Box(Modifier.padding(10.dp).size(width=44.dp,height=4.dp).background(Color(0xff737373),RoundedCornerShape(2.dp)))}
        ){
            Column(
                Modifier.fillMaxWidth().heightIn(max=360.dp).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=6.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ){
                when(kind){
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
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if(library)FxLibrary(m,{fxPack.launch(arrayOf("application/json","text/plain","application/xml","text/xml","application/octet-stream"))}){library=false}

    if(aiDialog){
        var style by remember {mutableStateOf("Automático")}
        AlertDialog(
            onDismissRequest={aiDialog=false},
            title={Text("✦ Monstro IA Auto Edit")},
            text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Detectado: ${m.videoPerception.label}",fontWeight=FontWeight.Bold,color=Color(0xff12d1e3))
                Text("A IA usa duração, fala, beats e quadros para montar uma edição editável. Se o Gemini atingir a cota, o modo local continua automaticamente.",fontSize=12.sp,color=Color.LightGray)
                Text(if(m.geminiReady)"Gemini configurado · ${GeminiSupport.GENERAL_MODEL}" else "Gemini não configurado · modo local disponível",fontSize=11.sp,color=Color.Gray)
                Choices(listOf("Automático","Trap / Música","Cinemático","Vlog / Conversa","Gameplay / Ação","Anime / Edit"),style){style=it}
            }},
            confirmButton={Button(onClick={aiDialog=false;m.runAiAutoEdit(style)}){Text("Analisar e editar")}},
            dismissButton={TextButton(onClick={aiDialog=false}){Text("Cancelar")}}
        )
    }

    if(mediaDialog)MonstroMediaPicker(
        onDismiss={mediaDialog=false},
        onVideos={uris->if(uris.isNotEmpty())m.importVideos(uris)},
        onPhotos={uris->
            if(m.clips.isEmpty())m.showMessage("Para usar fotos como camada, adicione um vídeo primeiro.")
            else m.importImages(uris)
        },
        fallbackVideo={mediaDialog=false;videos.launch(arrayOf("video/*"))},
        fallbackPhoto={mediaDialog=false;image.launch(arrayOf("image/*"))}
    )

    if(exportDialog)AlertDialog(
        onDismissRequest={exportDialog=false},
        title={Text("Exportar")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Proporção",fontWeight=FontWeight.Bold)
            Choices(listOf("9:16","16:9","1:1","4:5"),m.canvasRatio,m::selectCanvasRatio)
            Text("Fundo",fontWeight=FontWeight.Bold)
            Choices(listOf("Desfoque","Cor sólida","Padrão"),when(m.canvasBackground){"solid"->"Cor sólida";"pattern"->"Padrão";else->"Desfoque"}) {
                m.selectCanvasBackground(when(it){"Cor sólida"->"solid";"Padrão"->"pattern";else->"blur"})
            }
            Text("Resolução",fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=m.safeMode,onClick={if(!m.safeMode)m.toggleSafeMode()},label={Text("540p")})
                FilterChip(selected=!m.safeMode,onClick={if(m.safeMode)m.toggleSafeMode()},label={Text("1080p")})
            }
            Text("Frame rate",fontWeight=FontWeight.Bold)
            Choices(listOf("24 fps","30 fps","60 fps"),"${m.exportFps} fps"){m.selectExportFps(it.substringBefore(" ").toInt())}
            Text("Bitrate",fontWeight=FontWeight.Bold)
            Choices(listOf("Baixo","Recomendado","Alto"),when(m.bitrateMode){"low"->"Baixo";"high"->"Alto";else->"Recomendado"}){
                m.selectBitrateMode(when(it){"Baixo"->"low";"Alto"->"high";else->"recommended"})
            }
            Text("Codec",fontWeight=FontWeight.Bold)
            Choices(if(m.hevcSupported)listOf("H.264","HEVC") else listOf("H.264"),if(m.exportCodec=="HEVC")"HEVC" else "H.264"){
                m.selectExportCodec(if(it=="HEVC")"HEVC" else "H264")
            }
            Text("${m.exportFormat.width} × ${m.exportFormat.height} · ${m.exportFps} fps",fontSize=11.sp,color=Color.Gray)
        }},
        confirmButton={Button(onClick={exportDialog=false;m.export()},colors=ButtonDefaults.buttonColors(containerColor=Color(0xff12cfe0),contentColor=Color.Black)){Text("Exportar")}},
        dismissButton={TextButton(onClick={exportDialog=false}){Text("Voltar")}}
    )

    if(fullscreen && m.current!=null)Dialog(onDismissRequest={fullscreen=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding(),contentAlignment=Alignment.Center){
            Box(Modifier.fillMaxWidth().aspectRatio(m.exportFormat.aspect)){
                key(m.player,fullscreen){
                    AndroidView(
                        factory={EditorPlayerView(it).apply {useController=false;resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT}},
                        update={it.bind(m.player,true)},
                        modifier=Modifier.fillMaxSize()
                    )
                }
                AndroidView(factory={StudioPreview(it)},update={it.model=m;it.invalidate()},modifier=Modifier.fillMaxSize())
            }
            TextButton(onClick={fullscreen=false},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)){Text("×",fontSize=32.sp)}
        }
    }

    if(m.output!=null){
        AlertDialog(
            onDismissRequest={},
            title={Text("Vídeo pronto")},
            text={Text("A exportação terminou.")},
            confirmButton={Button(onClick={save.launch("MONSTRO_Studio.mp4")}){Text("Salvar MP4")}},
            dismissButton={TextButton(onClick=m::shareOutput){Text("Compartilhar")}}
        )
    }

    m.message?.let {
        AlertDialog(
            onDismissRequest=m::clearMessage,
            title={Text("Monstro")},
            text={Text(it)},
            confirmButton={TextButton(onClick=m::clearMessage){Text("OK")}}
        )
    }
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
    if(m.lyrics!=null){
        Text("Tradução por IA",fontWeight=FontWeight.Bold)
        Text("Traduz o texto e mantém o tempo das legendas na timeline.",fontSize=10.sp,color=Color.Gray)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            items(listOf("Português","Inglês","Espanhol","Francês","Alemão")){language->
                AssistChip(onClick={m.translateCaptions(language)},label={Text(language,fontSize=11.sp)})
            }
        }
        if(m.translationStatus.isNotBlank())Text(m.translationStatus,fontSize=11.sp)
    }
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
private fun FxInspector(m:EditorModel,library:()->Unit){
    OutlinedButton(onClick=library,modifier=Modifier.fillMaxWidth()){Text("+ Biblioteca de efeitos")}
    val layer=m.studio.fx.find {it.id==m.focusedId} ?: return
    val preset=FxCatalog.get(layer.presetId,m.studio.customFx)
    Text(preset?.name ?: "FX",fontWeight=FontWeight.Bold)
    Text("Efeito pronto aplicado ao clipe. Não precisa configurar parâmetros.",fontSize=11.sp,color=Color.Gray)
    TextButton(onClick={m.updateStudio(m.studio.copy(fx=m.studio.fx-layer))}){Text("Excluir efeito")}
}
@UnstableApi @Composable
private fun FxLibrary(m:EditorModel,importPack:()->Unit,close:()->Unit){
    var query by remember{mutableStateOf("")}
    var category by remember{mutableStateOf("Todos")}
    val customIds=remember(m.studio.customFx){m.studio.customFx.map {it.id}.toSet()}
    val categories=remember(m.studio.customFx){(FxCatalog.categories+m.studio.customFx.map {it.category}).distinct()}
    val results=remember(query,category,m.studio.favorites,m.studio.recent,m.studio.customFx){
        FxCatalog.search(query,category.takeIf {it !in listOf("Todos","Favoritos","Recentes","Meus efeitos")},m.studio.customFx)
            .filter {preset->when(category){
                "Favoritos"->preset.id in m.studio.favorites
                "Recentes"->preset.id in m.studio.recent
                "Meus efeitos"->preset.id in customIds
                else->true
            }}
            .let {list->if(category=="Recentes")list.sortedBy {p->m.studio.recent.indexOf(p.id)} else list}
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize().systemBarsPadding(),color=Color(0xff101017)){
            Column(Modifier.padding(16.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text("EFEITOS",fontWeight=FontWeight.Black,modifier=Modifier.weight(1f))
                    TextButton(onClick=importPack){Text("↓ Importar")}
                    TextButton(onClick=close){Text("Fechar")}
                }
                Text("Toque em um efeito e ele é aplicado pronto. Pacotes .monstrofx e presets .prfpset do Premiere ficam em Meus efeitos.",fontSize=12.sp,color=Color.Gray)
                OutlinedTextField(value=query,onValueChange={query=it},label={Text("Pesquisar FX")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Choices(listOf("Todos","Meus efeitos","Favoritos","Recentes")+categories,category){category=it}
                Text("${results.size} resultados",fontSize=11.sp,color=Color.Gray)
                LazyColumn {
                    items(results,key={it.id}){preset->
                        Row(
                            Modifier.fillMaxWidth().clickable {m.addFx(preset);close()}.padding(vertical=7.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ){
                            Box(Modifier.size(42.dp).background(Color.hsv(preset.engine*18f,.65f,.65f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Text("FX",fontWeight=FontWeight.Black)}
                            Column(Modifier.weight(1f).padding(start=12.dp)){
                                Text(preset.name,fontSize=13.sp)
                                Text(if(preset.id in customIds)"Importado · ${preset.category}" else preset.category,fontSize=10.sp,color=Color.Gray)
                            }
                            TextButton(onClick={m.favorite(preset.id)}){Text(if(preset.id in m.studio.favorites)"★" else "☆",fontSize=22.sp)}
                        }
                    }
                }
            }
        }
    }
}
