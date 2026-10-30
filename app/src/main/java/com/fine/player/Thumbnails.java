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
    private Bitmap extract(DownloadRequest request)throws Exception{
        String mp4=app.library().get(request.id,"mp4");
        if(!mp4.isEmpty()){
            MediaMetadataRetriever retriever=new MediaMetadataRetriever();
            try { retriever.setDataSource(app,Uri.parse(mp4)); return frame(retriever); }
            finally { retriever.release(); }
        }
        OfflineHls hls=app.offlineHls(request);
        int selected=0;
        if(hls.root instanceof HlsMultivariantPlaylist){
            HlsMultivariantPlaylist master=(HlsMultivariantPlaylist)hls.root;
            if(master.variants.isEmpty())return null;
            selected=hls.urls.indexOf(master.variants.get(0).url);
        }
        HlsMediaPlaylist media=hls.playlists.get(selected);int attempts=0;

        for(HlsMediaPlaylist.Segment segment:media.segments){
            if(segment.hasGapTag)continue;if(++attempts>3)break;
            File temp=File.createTempFile("cover-",".media",app.getCacheDir());
            MediaMetadataRetriever retriever=new MediaMetadataRetriever();
            try{
                try(OutputStream out=new FileOutputStream(temp)){
                    if(segment.initializationSegment!=null)hls.writeSegment(media,segment.initializationSegment,out);
                    hls.writeSegment(media,segment,out);
                }
                retriever.setDataSource(temp.getPath());
                Bitmap frame=frame(retriever);
                if(frame!=null){
                    if(frame.getWidth()>640){Bitmap scaled=Bitmap.createScaledBitmap(frame,480,Math.max(1,480*frame.getHeight()/frame.getWidth()),true);frame.recycle();return scaled;}
                    return frame;
                }
            }catch(Exception ignored){}finally{retriever.release();temp.delete();}
        }
        return null;
    }
    private Bitmap frame(MediaMetadataRetriever retriever){
        return Build.VERSION.SDK_INT>=27?retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST,480,270):retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST);
    }
    void remove(String id){worker.execute(()->new File(app.getFilesDir(),"covers/"+Links.id(id)+".jpg").delete());failed.remove(id);}
}
