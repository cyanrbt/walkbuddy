package com.cyanrbt.walkbuddy;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.TextureView;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Experimental Surface copy against a frozen producer. Never used by the application. */
final class SurfaceCopyProbe {
    static JSONObject compare(Instrumentation runner,TextureView view,int sensor,int display,int width,int height) throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())throw new AssertionError("Surface copy experiment requires an off-main caller");
        int[] before=onMain(runner,()->reference(view));
        Surface source=onMain(runner,()->new Surface(view.getSurfaceTexture()));
        Bitmap target=Bitmap.createBitmap(view.getResources().getDisplayMetrics(),16,12,Bitmap.Config.ARGB_8888);
        CountDownLatch completed=new CountDownLatch(1);
        Object lock=new Object();boolean[] finished={false},abandoned={false};int[] status={-1};
        long started=SystemClock.elapsedRealtimeNanos(),submitted;
        try {
            // API 26 performs the copy synchronously before posting its callback.
            // This method runs on the instrumentation thread on every Android version.
            try {
                PixelCopy.request(source,target,result->{
                    synchronized(lock){
                        finished[0]=true;status[0]=result;
                        if(abandoned[0]){target.recycle();source.release();}
                        completed.countDown();
                    }
                },new Handler(Looper.getMainLooper()));
            } catch(RuntimeException error){synchronized(lock){finished[0]=true;}throw error;}
            submitted=SystemClock.elapsedRealtimeNanos();
            if(!completed.await(5,TimeUnit.SECONDS))throw new AssertionError("Surface copy callback timeout");
            long ended=SystemClock.elapsedRealtimeNanos();
            int[] actual=new int[192];target.getPixels(actual,0,16,0,0,16,12);
            int[] after=onMain(runner,()->reference(view));
            int different=0,unstable=0,first=-1;
            for(int i=0;i<192;i++){
                if(before[i]!=after[i])unstable++;
                if(before[i]!=actual[i]){different++;if(first<0)first=i;}
            }
            JSONObject result=new JSONObject().put("sensor",sensor).put("display",display)
                .put("viewWidth",width).put("viewHeight",height).put("result",status[0])
                .put("referenceStable",unstable==0).put("referenceChangedPixels",unstable)
                .put("differentPixels",different).put("firstDifference",first)
                .put("referenceArgbSha256",digest(before)).put("surfaceArgbSha256",digest(actual))
                .put("referenceRgb",total(before)).put("surfaceRgb",total(actual))
                .put("requestCallNs",submitted-started).put("callbackLatencyNs",ended-started)
                .put("requestedOffMain",Looper.myLooper()!=Looper.getMainLooper())
                .put("accepted",status[0]==PixelCopy.SUCCESS && unstable==0 && different==0);
            if(first>=0)result.put("referenceFirstArgb",before[first]).put("surfaceFirstArgb",actual[first]);
            return result;
        } finally {
            // A timed-out native copy may still write: let its callback release the bitmap.
            synchronized(lock){
                if(finished[0]){target.recycle();source.release();}
                else abandoned[0]=true;
            }
        }
    }

    static int[] reference(TextureView view) {
        Bitmap bitmap=view.getBitmap(16,12);
        if(bitmap==null)throw new AssertionError("Frozen TextureView reference unavailable");
        try{int[] pixels=new int[192];bitmap.getPixels(pixels,0,16,0,0,16,12);return pixels;}
        finally{bitmap.recycle();}
    }
    static int total(int[] pixels){int sum=0;for(int c:pixels)sum+=((c>>16)&255)+((c>>8)&255)+(c&255);return sum;}
    private static String digest(int[] pixels) throws Exception {
        ByteBuffer buffer=ByteBuffer.allocate(pixels.length*4);for(int c:pixels)buffer.putInt(c);
        StringBuilder out=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(buffer.array()))out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    static <T>T onMain(Instrumentation runner,Callable<T> action) {
        AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        runner.runOnMainSync(()->{try{value.set(action.call());}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());return value.get();
    }
}
