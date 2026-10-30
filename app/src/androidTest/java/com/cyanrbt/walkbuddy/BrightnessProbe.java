package com.cyanrbt.walkbuddy;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Debug;
import android.os.SystemClock;
import android.view.TextureView;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Frozen released brightness path on actual Android TextureViews; no camera opened here. */
final class BrightnessProbe {
    static JSONObject baseline(Instrumentation runner,TextureView view,int sensor,int display,int width,int height) {
        AtomicReference<JSONObject> result=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        runner.runOnMainSync(()->{try {
            long before=SystemClock.elapsedRealtimeNanos();Bitmap bitmap=view.getBitmap(16,12);
            long copied=SystemClock.elapsedRealtimeNanos();
            if(bitmap==null)throw new AssertionError("Brightness baseline bitmap missing");
            try {
                long cpu=Debug.threadCpuTimeNanos();int total=legacyTotal(bitmap);
                long sumCpu=Debug.threadCpuTimeNanos()-cpu;
                result.set(new JSONObject().put("sensor",sensor).put("display",display)
                    .put("width",width).put("height",height).put("totalRgb",total)
                    .put("bitmapBytes",bitmap.getAllocationByteCount()).put("copyWallNs",copied-before)
                    .put("sumCpuNs",sumCpu).put("argbSha256",digest(bitmap)));
            } finally {bitmap.recycle();}
        } catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());
        return result.get();
    }
    /** v0.4.36 loop, kept independent from the new bulk extraction. */
    static int legacyTotal(Bitmap bitmap) {
        int light=0;
        for(int y=0;y<12;y++)for(int x=0;x<16;x++) {
            int c=bitmap.getPixel(x,y);light+=Color.red(c)+Color.green(c)+Color.blue(c);
        }
        return light;
    }
    private static String digest(Bitmap bitmap) throws Exception {
        ByteBuffer data=ByteBuffer.allocate(16*12*4);
        for(int y=0;y<12;y++)for(int x=0;x<16;x++)data.putInt(bitmap.getPixel(x,y));
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(data.array());StringBuilder text=new StringBuilder();
        for(byte b:bytes)text.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return text.toString();
    }
}
