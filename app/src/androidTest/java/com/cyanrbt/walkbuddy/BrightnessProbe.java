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
import java.util.Arrays;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import org.json.JSONArray;
import org.json.JSONObject;

/** Frozen released brightness path on actual Android TextureViews; no camera opened here. */
final class BrightnessProbe {
    private static Bitmap firstBitmap;
    private static int[] firstPixels;
    static JSONObject compare(Instrumentation runner,TextureView view,BrightnessSampler sampler,int sensor,int display,int width,int height) {
        JSONObject expected=baseline(runner,view,sensor,display,width,height);
        onMain(runner,()->{
            long wall=SystemClock.elapsedRealtimeNanos(),cpu=Debug.threadCpuTimeNanos();int total=sampler.read(view);
            long finished=SystemClock.elapsedRealtimeNanos(),cpuFinished=Debug.threadCpuTimeNanos();
            Bitmap storage=(Bitmap)field(sampler,"bitmap");int[] pixels=(int[])field(sampler,"pixels");
            if(total!=expected.getInt("totalRgb") || !digest(storage).equals(expected.getString("argbSha256")))
                throw new AssertionError("Brightness changed displayed pixels/total s="+sensor+" d="+display);
            for(int i=0;i<pixels.length;i++)if(pixels[i]!=storage.getPixel(i%16,i/16))throw new AssertionError("Bulk pixel mismatch");
            if(firstBitmap==null){firstBitmap=storage;firstPixels=pixels;}
            if(storage!=firstBitmap || pixels!=firstPixels)throw new AssertionError("Brightness workspace was reallocated");
            expected.put("bulkReadWallNs",finished-wall).put("bulkReadCpuNs",cpuFinished-cpu)
                .put("argbIdentical",true).put("totalIdentical",true).put("bitmapReused",true).put("pixelsReused",true);
        });
        return expected;
    }
    static void finish(BrightnessSampler sampler) {
        if(firstBitmap==null || firstBitmap.isRecycled())throw new AssertionError("Brightness workspace missing before close");
        sampler.close();sampler.close();
        if(!firstBitmap.isRecycled() || field(sampler,"bitmap")!=null)throw new AssertionError("Brightness close retained bitmap");
        firstBitmap=null;firstPixels=null;
    }
    static String synthetic(Instrumentation runner,File directory) {
        AtomicReference<String> result=new AtomicReference<>();
        onMain(runner,()->{
            FixedTexture view=new FixedTexture(runner.getTargetContext());BrightnessSampler sampler=new BrightnessSampler();
            JSONArray cases=new JSONArray(),trials=new JSONArray();
            try {
                for(int color:new int[]{Color.BLACK,Color.WHITE,0xff010101,0xff020202,0xff030303,0xff040404,
                        Color.RED,Color.GREEN,Color.BLUE,Color.TRANSPARENT,0x7f2f8e61}) {
                    Arrays.fill(view.content,color);syntheticCase(view,sampler,cases,"solid-"+Integer.toHexString(color));
                }
                Arrays.fill(view.content,0xff030303);view.content[0]=0xff020303;syntheticCase(view,sampler,cases,"below-threshold");
                if(cases.getJSONObject(cases.length()-1).getInt("totalRgb")!=1727)throw new AssertionError("Lower brightness boundary changed");
                view.content[0]=0xff040303;syntheticCase(view,sampler,cases,"above-threshold");
                if(cases.getJSONObject(cases.length()-1).getInt("totalRgb")!=1729)throw new AssertionError("Upper brightness boundary changed");
                for(int i=0;i<view.content.length;i++)view.content[i]=i*93847;
                syntheticCase(view,sampler,cases,"varying-alpha-rgb");
                view.partial=true;syntheticCase(view,sampler,cases,"partial-copy-empty-remainder");view.partial=false;
                view.noCopy=true;syntheticCase(view,sampler,cases,"no-copy-clears-old-brightness");view.noCopy=false;
                view.available=false;syntheticCase(view,sampler,cases,"unavailable-surface");view.available=true;
                view.throwCopy=true;
                try{sampler.read(view);throw new AssertionError("Copy failure was ignored");}catch(IllegalStateException expected){/* Error reaches caller. */}
                view.throwCopy=false;Arrays.fill(view.content,Color.WHITE);syntheticCase(view,sampler,cases,"copy-after-error");
                // Controlled bitmap-copy workload only: no GPU, camera, inference, or end-to-end performance claim.
                for(int i=0;i<100;i++){referenceRead(view);sampler.read(view);}
                for(boolean legacy:new boolean[]{true,false,false,true}) {
                    long wall=SystemClock.elapsedRealtimeNanos(),cpu=Debug.threadCpuTimeNanos(),checksum=0;
                    for(int i=0;i<2000;i++)checksum+=legacy?referenceRead(view):sampler.read(view);
                    long finished=SystemClock.elapsedRealtimeNanos(),cpuFinished=Debug.threadCpuTimeNanos();
                    if(checksum!=2000L*16*12*3*255)throw new AssertionError("Brightness benchmark result changed");
                    trials.put(new JSONObject().put("path",legacy?"legacy":"reused").put("iterations",2000)
                        .put("wallNs",finished-wall).put("cpuNs",cpuFinished-cpu).put("checksum",checksum));
                }
                Bitmap bitmap=(Bitmap)field(sampler,"bitmap");sampler.close();sampler.close();
                if(!bitmap.isRecycled() || field(sampler,"bitmap")!=null)throw new AssertionError("Closed brightness bitmap retained");
                try{sampler.read(view);throw new AssertionError("Closed sampler revived");}catch(IllegalStateException expected){/* Closed stays closed. */}
                JSONObject report=new JSONObject().put("scope","Controlled native bitmap-copy workload; not camera/GPU or device performance")
                    .put("cases",cases).put("trials",trials).put("closedBitmapRecycled",true).put("closedReadRejected",true);
                try(FileOutputStream stream=new FileOutputStream(new File(directory,"brightness-synthetic.json"))) {
                    stream.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                result.set("BRIGHTNESS_SYNTHETIC_PASS cases="+cases.length()+" trials=4 iterations=2000; exact old sums, threshold, alpha, partial/no copy, unavailable, error, close\n");
            } finally {sampler.close();}
        });
        return result.get();
    }
    private static void syntheticCase(FixedTexture view,BrightnessSampler sampler,JSONArray cases,String name) throws Exception {
        int expected=referenceRead(view),actual=sampler.read(view);
        if(expected!=actual)throw new AssertionError("Brightness differs in "+name+": "+expected+" / "+actual);
        cases.put(new JSONObject().put("name",name).put("totalRgb",actual).put("dark",actual>=0 && actual<1728));
    }
    private static int referenceRead(TextureView view) {
        Bitmap bitmap=view.getBitmap(16,12);if(bitmap==null)return -1;
        try{return legacyTotal(bitmap);}finally{bitmap.recycle();}
    }
    private static final class FixedTexture extends TextureView {
        final int[] content=new int[192];boolean available=true,noCopy,partial,throwCopy;
        FixedTexture(android.content.Context context){super(context);}
        @Override public boolean isAvailable(){return available;}
        @Override public Bitmap getBitmap(int width,int height) {
            return available?getBitmap(Bitmap.createBitmap(getResources().getDisplayMetrics(),width,height,Bitmap.Config.ARGB_8888)):null;
        }
        @Override public Bitmap getBitmap(Bitmap target) {
            if(throwCopy)throw new IllegalStateException("Injected brightness copy failure");
            if(!noCopy)target.setPixels(content,0,16,0,0,16,partial?6:12);
            return target;
        }
    }
    private static Object field(Object value,String name) {
        try{Field field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(value);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private interface Check {void run() throws Exception;}
    private static void onMain(Instrumentation runner,Check check) {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        runner.runOnMainSync(()->{try{check.run();}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());
    }
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
