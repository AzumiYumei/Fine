package com.fine.player;

import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.widget.ImageView;
import androidx.media3.common.C;
import androidx.media3.common.util.UriUtil;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.hls.playlist.*;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.*;

@androidx.media3.common.util.UnstableApi
final class Thumbnails {
    private final FineApp app;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<String> pending=new HashSet<>(),failed=new HashSet<>();
    private final Map<String,List<java.lang.ref.WeakReference<ImageView>>> waiting=new HashMap<>();
    Thumbnails(FineApp app){this.app=app;}
    void load(DownloadRequest request,ImageView view){
        view.setTag(request.id);
        if(!waiting.containsKey(request.id))waiting.put(request.id,new ArrayList<>());
        waiting.get(request.id).add(new java.lang.ref.WeakReference<>(view));
        if(failed.contains(request.id)){waiting.remove(request.id);return;}
        if(!pending.add(request.id))return;
        worker.execute(()->{
            Bitmap result=null;
            File image=new File(app.getFilesDir(),"covers/"+Links.id(request.id)+".jpg");
            try{
                if(image.exists())result=BitmapFactory.decodeFile(image.getPath());
                if(result==null){
                    result=extract(request);
                    if(result!=null){image.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(image)){result.compress(Bitmap.CompressFormat.JPEG,85,out);}}
                }
            }catch(Exception ignored){ }
            Bitmap bitmap=result;
            main.post(()->{
                pending.remove(request.id);if(bitmap==null)failed.add(request.id);
                List<java.lang.ref.WeakReference<ImageView>> targets=waiting.remove(request.id);
                if(targets!=null)for(java.lang.ref.WeakReference<ImageView> ref:targets){
                    ImageView target=ref.get();if(target!=null&&request.id.equals(target.getTag())&&bitmap!=null){target.setScaleType(ImageView.ScaleType.CENTER_CROP);target.setImageBitmap(bitmap);}
                }
            });
        });
    }
    private HlsPlaylist playlist(Uri uri,DownloadRequest request)throws IOException{
        return ParsingLoadable.load(app.source(true).createDataSource(),new GapPlaylistParser(new HlsPlaylistParser(),app.problems().skipped(request.id)),uri,C.DATA_TYPE_MANIFEST);
    }
    private Bitmap extract(DownloadRequest request)throws Exception{
        HlsPlaylist root=playlist(request.uri,request);
        if(!request.streamKeys.isEmpty())root=root.copy(request.streamKeys);
        if(root instanceof HlsMultivariantPlaylist){
            HlsMultivariantPlaylist master=(HlsMultivariantPlaylist)root;
            if(master.variants.isEmpty())return null;
            root=playlist(master.variants.get(0).url,request);
        }
        HlsMediaPlaylist media=(HlsMediaPlaylist)root;int attempts=0;
        for(HlsMediaPlaylist.Segment segment:media.segments){
            if(segment.hasGapTag)continue;if(++attempts>3)break;
            File temp=File.createTempFile("cover-",".media",app.getCacheDir());
            MediaMetadataRetriever retriever=new MediaMetadataRetriever();
            try{
                try(OutputStream out=new FileOutputStream(temp)){
                    if(segment.initializationSegment!=null)write(media,segment.initializationSegment,out);
                    write(media,segment,out);
                }
                retriever.setDataSource(temp.getPath());
                Bitmap frame=Build.VERSION.SDK_INT>=27?retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST,480,270):retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST);
                if(frame!=null){
                    if(frame.getWidth()>640){Bitmap scaled=Bitmap.createScaledBitmap(frame,480,Math.max(1,480*frame.getHeight()/frame.getWidth()),true);frame.recycle();return scaled;}
                    return frame;
                }
            }catch(Exception ignored){}finally{retriever.release();temp.delete();}
        }
        return null;
    }
    private void write(HlsMediaPlaylist media,HlsMediaPlaylist.Segment segment,OutputStream out)throws Exception{
        DataSpec spec=new DataSpec(UriUtil.resolveToUri(media.baseUri,segment.url),segment.byteRangeOffset,segment.byteRangeLength);
        try(InputStream raw=new DataSourceInputStream(app.source(true).createDataSource(),spec)){
            InputStream input=raw;
            if(segment.fullSegmentEncryptionKeyUri!=null){
                byte[] key;
                try(InputStream source=new DataSourceInputStream(app.source(true).createDataSource(),new DataSpec(UriUtil.resolveToUri(media.baseUri,segment.fullSegmentEncryptionKeyUri)))){
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();int b;
                    while((b=source.read())!=-1){if(bytes.size()>=16)throw new IOException("Invalid AES key");bytes.write(b);}key=bytes.toByteArray();
                }
                byte[] value=new BigInteger(segment.encryptionIV.replaceFirst("^0[xX]",""),16).toByteArray(),iv=new byte[16];
                System.arraycopy(value,Math.max(0,value.length-16),iv,Math.max(0,16-value.length),Math.min(16,value.length));
                Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));input=new CipherInputStream(raw,cipher);
            }
            byte[] buffer=new byte[32768];int n;long count=0;
            while((n=input.read(buffer))!=-1){count+=n;if(count>32*1024*1024)throw new IOException("Thumbnail segment too large");out.write(buffer,0,n);}
        }
    }
    void remove(String id){worker.execute(()->new File(app.getFilesDir(),"covers/"+Links.id(id)+".jpg").delete());failed.remove(id);}
}
