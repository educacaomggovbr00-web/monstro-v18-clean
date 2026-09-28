package com.monstro.v18

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DeviceMediaItem(
    val uri:Uri,
    val name:String,
    val durationMs:Long,
    val isVideo:Boolean
)

private fun requiredMediaPermissions():Array<String> =
    if(Build.VERSION.SDK_INT>=33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_IMAGES)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun hasMediaPermission(context:Context):Boolean =
    requiredMediaPermissions().all {ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED}

private fun queryDeviceMedia(context:Context,videos:Boolean):List<DeviceMediaItem>{
    val resolver=context.contentResolver
    val collection=if(videos)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection=if(videos) arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION
    ) else arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME
    )
    val result=mutableListOf<DeviceMediaItem>()
    resolver.query(
        collection,projection,null,null,
        MediaStore.MediaColumns.DATE_ADDED+" DESC"
    )?.use {cursor->
        val idIndex=cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameIndex=cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val durationIndex=if(videos)cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
        while(cursor.moveToNext() && result.size<500){
            val id=cursor.getLong(idIndex)
            val name=cursor.getString(nameIndex) ?: if(videos)"Vídeo" else "Foto"
            val duration=if(durationIndex>=0)cursor.getLong(durationIndex).coerceAtLeast(0) else 0
            result+=DeviceMediaItem(ContentUris.withAppendedId(collection,id),name,duration,videos)
        }
    }
    return result
}

private fun oldThumbnail(context:Context,item:DeviceMediaItem):Bitmap?{
    return runCatching {
        if(item.isVideo){
            val r=MediaMetadataRetriever()
            try{
                r.setDataSource(context,item.uri)
                r.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }finally{r.release()}
        }else{
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            context.contentResolver.openInputStream(item.uri)?.use {BitmapFactory.decodeStream(it,null,bounds)}
            var sample=1
            while(maxOf(bounds.outWidth,bounds.outHeight)/sample>360)sample*=2
            val opts=BitmapFactory.Options().apply{inSampleSize=sample}
            context.contentResolver.openInputStream(item.uri)?.use {BitmapFactory.decodeStream(it,null,opts)}
        }
    }.getOrNull()
}

@Composable
private fun DeviceMediaThumb(item:DeviceMediaItem){
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue=null,item.uri){
        value=withContext(Dispatchers.IO){
            runCatching {
                if(Build.VERSION.SDK_INT>=29)
                    context.contentResolver.loadThumbnail(item.uri,Size(360,360),null)
                else oldThumbnail(context,item)
            }.getOrNull()
        }
    }
    if(bitmap!=null){
        Image(
            bitmap=bitmap!!.asImageBitmap(),
            contentDescription=item.name,
            modifier=Modifier.fillMaxSize(),
            contentScale=ContentScale.Crop
        )
    }else{
        Box(Modifier.fillMaxSize().background(Color(0xff292929)),contentAlignment=Alignment.Center){
            Text(if(item.isVideo)"▶" else "▣",color=Color.Gray,fontSize=20.sp)
        }
    }
}

private fun mediaDuration(ms:Long):String{
    val sec=ms/1000
    return String.format("%02d:%02d",sec/60,sec%60)
}

@Composable
fun MonstroMediaPicker(
    onDismiss:()->Unit,
    onVideos:(List<Uri>)->Unit,
    onPhotos:(List<Uri>)->Unit,
    fallbackVideo:()->Unit,
    fallbackPhoto:()->Unit
){
    val context=LocalContext.current
    var tab by remember{mutableStateOf("Vídeos")}
    var refresh by remember{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf<Set<String>>(emptySet())}

    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        refresh++
    }

    LaunchedEffect(Unit){
        if(!hasMediaPermission(context))permissionLauncher.launch(requiredMediaPermissions())
    }

    val permitted=remember(refresh){hasMediaPermission(context)}
    val media by produceState<List<DeviceMediaItem>>(initialValue=emptyList(),tab,refresh,permitted){
        value=if(permitted)withContext(Dispatchers.IO){queryDeviceMedia(context,tab=="Vídeos")} else emptyList()
    }

    LaunchedEffect(tab){selected=emptySet()}

    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize().systemBarsPadding(),color=Color(0xff111111),contentColor=Color.White){
            Column(Modifier.fillMaxSize()){
                Row(
                    Modifier.fillMaxWidth().height(58.dp).padding(horizontal=8.dp),
                    verticalAlignment=Alignment.CenterVertically
                ){
                    TextButton(onClick=onDismiss,contentPadding=PaddingValues(6.dp)){Text("×",fontSize=34.sp,color=Color.White)}
                    Text("Álbuns",fontSize=18.sp,fontWeight=FontWeight.Medium)
                    Text("⌄",fontSize=18.sp)
                    Spacer(Modifier.weight(1f))
                    Text("MONSTRO",fontSize=11.sp,color=Color(0xff18d5e5),fontWeight=FontWeight.Bold)
                }
                HorizontalDivider(color=Color(0xff2c2c2c))
                Row(Modifier.fillMaxWidth().height(54.dp)){
                    listOf("Vídeos","Fotos").forEach {name->
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .clickable{tab=name}
                                .border(
                                    width=if(tab==name)2.dp else 0.dp,
                                    color=if(tab==name)Color(0xff12d1e3) else Color.Transparent
                                ),
                            contentAlignment=Alignment.Center
                        ){
                            Text(name,color=if(tab==name)Color(0xff12d1e3) else Color.Gray,fontWeight=FontWeight.Bold)
                        }
                    }
                }

                if(!permitted){
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(28.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.Center
                    ){
                        Text("Permita acesso às suas mídias",fontWeight=FontWeight.Bold,fontSize=18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("O MONSTRO usa essa permissão somente para mostrar vídeos e fotos neste seletor.",color=Color.Gray,fontSize=12.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick={permissionLauncher.launch(requiredMediaPermissions())}){Text("Permitir acesso")}
                        TextButton(onClick={if(tab=="Vídeos")fallbackVideo else fallbackPhoto}){Text("Usar seletor do Android")}
                    }
                }else if(media.isEmpty()){
                    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
                        Text(if(tab=="Vídeos")"Nenhum vídeo encontrado" else "Nenhuma foto encontrada",color=Color.Gray)
                    }
                }else{
                    LazyVerticalGrid(
                        columns=GridCells.Fixed(4),
                        modifier=Modifier.weight(1f).fillMaxWidth(),
                        contentPadding=PaddingValues(1.dp),
                        verticalArrangement=Arrangement.spacedBy(1.dp),
                        horizontalArrangement=Arrangement.spacedBy(1.dp)
                    ){
                        items(media,key={it.uri.toString()}){item->
                            val key=item.uri.toString()
                            val checked=key in selected
                            Box(
                                Modifier.aspectRatio(.78f).background(Color.Black)
                                    .clickable{
                                        selected=if(checked)selected-key else selected+key
                                    }
                            ){
                                DeviceMediaThumb(item)
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(7.dp).size(22.dp)
                                        .background(if(checked)Color(0xff12d1e3) else Color(0x33000000),CircleShape)
                                        .border(2.dp,Color.White,CircleShape),
                                    contentAlignment=Alignment.Center
                                ){
                                    if(checked)Text("✓",color=Color.Black,fontWeight=FontWeight.Black,fontSize=12.sp)
                                }
                                if(item.isVideo){
                                    Text(
                                        mediaDuration(item.durationMs),
                                        modifier=Modifier.align(Alignment.BottomEnd).padding(5.dp)
                                            .background(Color(0x66000000),RoundedCornerShape(3.dp)).padding(horizontal=3.dp,vertical=1.dp),
                                        color=Color.White,fontSize=10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(color=Color(0xff1c1c1c),modifier=Modifier.fillMaxWidth()){
                    Row(
                        Modifier.fillMaxWidth().height(72.dp).padding(horizontal=16.dp),
                        verticalAlignment=Alignment.CenterVertically
                    ){
                        Text(
                            if(selected.isEmpty())"Selecione mídias" else "${selected.size} selecionada(s)",
                            color=Color.LightGray,fontSize=12.sp,
                            modifier=Modifier.weight(1f)
                        )
                        Button(
                            onClick={
                                val chosen=media.filter {it.uri.toString() in selected}.map {it.uri}
                                if(tab=="Vídeos")onVideos(chosen) else onPhotos(chosen)
                                onDismiss()
                            },
                            enabled=selected.isNotEmpty(),
                            colors=ButtonDefaults.buttonColors(containerColor=Color(0xff12d1e3),contentColor=Color.Black),
                            shape=RoundedCornerShape(10.dp)
                        ){Text("Adicionar",fontWeight=FontWeight.Bold)}
                    }
                }
            }
        }
    }
}
