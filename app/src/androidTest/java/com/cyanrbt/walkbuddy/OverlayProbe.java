package com.cyanrbt.walkbuddy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Camera-free raster regression for masks, dash gaps, redraw reuse, resize and clear. */
final class OverlayProbe {
    static void run(Instrumentation runner) {
        Bundle result=new Bundle();
        StringBuilder report=new StringBuilder();
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("Overlay probe is restricted to emulators");
            AtomicReference<Throwable> failure=new AtomicReference<>();
            runner.runOnMainSync(()->{try{check(runner,report);}catch(Throwable error){failure.set(error);}});
            if(failure.get()!=null)throw new AssertionError(failure.get());
            result.putString("stream","OVERLAY_PROBE_PASS: yellow depth dashes, solid gray/red, cached redraw, resize, clear, narrow label; "+report);
            runner.finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream",report+"OVERLAY_PROBE_FAIL: "+error);
            runner.finish(Activity.RESULT_CANCELED,result);
        }
    }

    private static void check(Instrumentation runner,StringBuilder report) throws Exception {
        DetectionOverlay overlay=new DetectionOverlay(runner.getTargetContext(),null);
        float dp=runner.getTargetContext().getResources().getDisplayMetrics().density;
        int w=Math.round(400*dp),h=Math.round(300*dp);
        overlay.layout(0,0,w,h);
        PreviewGeometry geometry=new PreviewGeometry(640,480,0,0,w,h);
        boolean[] pixels=new boolean[64*96];
        for(int y=24;y<72;y++)for(int x=16;x<48;x++)pixels[y*64+x]=true;
        ObstacleMask mask=new ObstacleMask(64,96,pixels);
        for(Detection.Risk risk:Detection.Risk.values()) {
            List<Detection> data=Collections.singletonList(new Detection("空间障碍",1,.25f,.25f,.75f,.75f,risk,1.2f,mask));
            overlay.show(data,geometry);
            Object entries=entries(overlay);
            Bitmap first=draw(overlay,w,h),repeat=null;
            try {
                int bright=0,gaps=0;
                for(int i=0;i<100;i++) {
                    int alpha=Color.alpha(first.getPixel(Math.round(100*dp),Math.round((100+i)*dp)));
                    if(alpha>160)bright++;
                    if(alpha<80)gaps++;
                }
                if(risk==Detection.Risk.CAUTION) {
                    if(bright<20 || gaps<10)throw new AssertionError("Yellow depth boundary lost its dashes: "+bright+"/"+gaps);
                } else if(bright<95)throw new AssertionError("Gray/red boundary should stay solid: "+bright);
                if(Color.alpha(first.getPixel(w/2,h/2))<20)throw new AssertionError("Mask fill disappeared");
                if(first.getPixel(Math.round(10*dp),h/2)!=Color.TRANSPARENT)throw new AssertionError("Mask leaked outside occupancy");
                overlay.show(data,geometry);
                if(entries!=entries(overlay))throw new AssertionError("Identical snapshot rebuilt drawing paths");
                repeat=draw(overlay,w,h);
                if(!first.sameAs(repeat))throw new AssertionError("Cached redraw changed pixels");
                PreviewGeometry resized=new PreviewGeometry(640,480,0,0,w/2,h/2);
                overlay.show(data,resized);
                if(entries==entries(overlay))throw new AssertionError("Resize reused old coordinates");
                overlay.show(data,geometry);
                overlay.clear();
                Bitmap cleared=draw(overlay,w,h);
                try{assertEmpty(cleared);}finally{cleared.recycle();}
                overlay.show(data,geometry);
                overlay.show(data,null);
                Bitmap pending=draw(overlay,w,h);
                try{assertEmpty(pending);}finally{pending.recycle();}
            } finally {first.recycle();if(repeat!=null)repeat.recycle();}
        }
        checkLabels(runner,report);
        // A narrow PiP image must not send a negative label width to ellipsize.
        overlay.layout(0,0,1,1);
        overlay.show(Collections.singletonList(new Detection("很长的标签",1,0,0,1,1)),
            new PreviewGeometry(640,480,0,0,1,1));
        Bitmap tiny=draw(overlay,1,1);tiny.recycle();
    }

    private static void checkLabels(Instrumentation runner,StringBuilder report) throws Exception {
        ModelLabels labels;
        try(Reader original=new InputStreamReader(runner.getTargetContext().getAssets().open("labelmap.txt"),StandardCharsets.UTF_8);
            Reader translated=new InputStreamReader(runner.getTargetContext().getAssets().open("labelmap_zh.tsv"),StandardCharsets.UTF_8)) {
            labels=ModelLabels.read(original,translated);
        }
        File output=new File(runner.getTargetContext().getCacheDir(),"ui-probe");
        if(!output.isDirectory() && !output.mkdirs())throw new AssertionError("Overlay evidence directory unavailable");
        JSONArray samples=new JSONArray(),contrasts=new JSONArray();int cases=0,contrastCases=0;
        float previousSize=0;
        for(float font:new float[]{1f,1.5f,2f}) {
            Configuration config=new Configuration(runner.getTargetContext().getResources().getConfiguration());
            config.fontScale=font;config.densityDpi=160;
            Context context=runner.getTargetContext().createConfigurationContext(config);
            DetectionOverlay overlay=new DetectionOverlay(context,null);
            Paint text=(Paint)field(overlay,"textPaint");
            TextView reference=new TextView(context);reference.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
            if(Math.abs(text.getTextSize()-reference.getTextSize())>.01f || text.getTextSize()<=previousSize)
                throw new AssertionError("Overlay font does not follow system sp conversion: "+font+" / "+text.getTextSize());
            previousSize=text.getTextSize();
            float dp=context.getResources().getDisplayMetrics().density;
            for(int width:new int[]{96,160,320}) {
                int w=Math.round(width*dp),h=Math.round(width*.75f*dp);overlay.layout(0,0,w,h);
                PreviewGeometry geometry=new PreviewGeometry(640,480,0,0,w,h);
                for(int i=0;i<labels.size();i++)if(labels.defined(i)) {
                    String name=labels.displayName(i),full="未测距 · "+name;
                    overlay.show(Collections.singletonList(new Detection(name,.8f,.2f,.3f,.8f,.9f,Detection.Risk.CAUTION,Float.NaN,null)),geometry);
                    String shown=label(overlay);
                    if(!shown.startsWith("未测距") || text.measureText(full)<=geometry.width-12*dp && !full.equals(shown))
                        throw new AssertionError("Unmeasured qualifier/name lost: "+font+" / "+width+" / "+shown);
                    qualifiedLabel(shown,"未测距");
                    labelBounds(overlay,geometry,dp);cases++;
                    if("parking meter".equals(labels.category(i))) {
                        samples.put(sample(overlay,font,width,"unmeasured"));
                        save(overlay,w,h,new File(output,"overlay-font"+Math.round(font*100)+"-unmeasured-"+width+".png"));
                    }
                    overlay.show(Collections.singletonList(new Detection(name,.8f,.2f,.3f,.8f,.9f)),geometry);
                    shown=label(overlay);
                    if(shown.contains("未测距") || text.measureText(name)<=geometry.width-12*dp && !name.equals(shown))
                        throw new AssertionError("Gray label changed or acquired a qualifier");
                    labelBounds(overlay,geometry,dp);cases++;
                }
                overlay.show(Collections.singletonList(new Detection("空间障碍",1,.2f,.3f,.8f,.9f,Detection.Risk.NEAR,.7f,null)),geometry);
                if(!label(overlay).replace(" ","").startsWith("约0.7米"))throw new AssertionError("Measured qualifier lost: "+label(overlay));
                qualifiedLabel(label(overlay),"约 0.7 米");
                labelBounds(overlay,geometry,dp);cases++;
                samples.put(sample(overlay,font,width,"measured"));
                save(overlay,w,h,new File(output,"overlay-font"+Math.round(font*100)+"-measured-"+width+".png"));
                int letterboxHeight=h+Math.round(80*dp);overlay.layout(0,0,w,letterboxHeight);
                PreviewGeometry boxed=new PreviewGeometry(640,480,0,0,w,letterboxHeight);
                for(float[] bounds:new float[][]{{0,0,.25f,.25f},{.75f,0,1,.25f},{0,.75f,.25f,1},{.75f,.75f,1,1}})
                    for(boolean measured:new boolean[]{false,true}) {
                        overlay.show(Collections.singletonList(new Detection("停车让行标志",1,bounds[0],bounds[1],bounds[2],bounds[3],
                            measured?Detection.Risk.NEAR:Detection.Risk.CAUTION,measured?.7f:Float.NaN,null)),boxed);
                        qualifiedLabel(label(overlay),measured?"约 0.7 米":"未测距");
                        labelBounds(overlay,boxed,dp);cases++;
                    }
            }
            for(int[] size:new int[][]{{1,1},{96,12},{10,200}}) {
                int w=Math.round(size[0]*dp),h=Math.round(size[1]*dp);overlay.layout(0,0,w,h);
                overlay.show(Collections.singletonList(new Detection("空间障碍",1,0,0,1,1,Detection.Risk.NEAR,.7f,null)),
                    new PreviewGeometry(640,480,0,0,w,h));
                if(!label(overlay).isEmpty())throw new AssertionError("Insufficient space retained a clipped or reduced label");
                Bitmap tiny=draw(overlay,w,h);tiny.recycle();cases++;
            }
            contrastCases+=checkContrast(overlay,font,dp,contrasts);
            report.append("LABEL_FONT_PASS scale=").append(font).append(" pixels=").append(text.getTextSize()).append('\n');
        }
        JSONObject data=new JSONObject().put("scope","Native overlay layout fixtures, not real object or depth recognition")
            .put("layout_cases",cases).put("contrast_cases",contrastCases).put("samples",samples).put("contrasts",contrasts);
        try(FileOutputStream stream=new FileOutputStream(new File(output,"overlay-label-fonts.json"))) {
            stream.write(data.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        report.append("LABEL_LAYOUT_PASS cases=").append(cases).append("; 80 Chinese classes, fonts 100/150/200%, widths 96/160/320dp, glyph/bubble bounds, letterbox corners, tiny views; ")
            .append("LABEL_CONTRAST_PASS cases=").append(contrastCases).append("; white/black backgrounds, minimum 4.5:1\n");
    }
    private static void qualifiedLabel(String shown,String qualifier) {
        if(shown.equals(qualifier) || shown.equals(qualifier.replace(" ","")))return;
        String prefix=qualifier+" · ";
        if(!shown.startsWith(prefix) || shown.substring(prefix.length()).replace("…","").trim().isEmpty())
            throw new AssertionError("Qualifier has a partial or empty object tail: "+shown);
    }
    private static JSONObject sample(DetectionOverlay overlay,float font,int width,String mode) throws Exception {
        Object entry=((Object[])entries(overlay))[0];
        return new JSONObject().put("font_scale",font).put("width_dp",width).put("mode",mode).put("label",label(overlay))
            .put("text_pixels",((Paint)field(overlay,"textPaint")).getTextSize())
            .put("bubble_top",(float)field(entry,"labelTop")).put("bubble_bottom",(float)field(entry,"labelBottom"));
    }
    private static void labelBounds(DetectionOverlay overlay,PreviewGeometry geometry,float dp) throws Exception {
        Object entry=((Object[])entries(overlay))[0];String shown=label(overlay);
        if(shown.isEmpty())throw new AssertionError("Expected a readable label");
        float x=(float)field(entry,"labelX"),top=(float)field(entry,"labelTop"),bottom=(float)field(entry,"labelBottom");
        float baseline=(float)field(entry,"labelY"),width=(float)field(entry,"labelWidth");
        Paint text=(Paint)field(overlay,"textPaint");Rect glyph=new Rect();text.getTextBounds(shown,0,shown.length(),glyph);
        if(x<geometry.left-.01f || x+width+8*dp>geometry.left+geometry.width+.01f
            || top<geometry.top-.01f || bottom>geometry.top+geometry.height+.01f
            || baseline+glyph.top<top+4*dp-.01f || baseline+glyph.bottom>bottom-4*dp+.01f
            || x+4*dp+glyph.left<x-.01f || x+4*dp+glyph.right>x+width+8*dp+.01f)
            throw new AssertionError("Glyph or background clipped: "+shown+" / "+geometry.viewWidth+"x"+geometry.viewHeight);
    }
    private static int checkContrast(DetectionOverlay overlay,float font,float dp,JSONArray rows) throws Exception {
        int count=0,w=Math.round(320*dp),h=Math.round(240*dp);overlay.layout(0,0,w,h);
        PreviewGeometry geometry=new PreviewGeometry(640,480,0,0,w,h);
        for(Detection.Risk risk:Detection.Risk.values())for(int background:new int[]{Color.WHITE,Color.BLACK}) {
            overlay.show(Collections.singletonList(new Detection("空间障碍",1,.2f,.8f,.8f,1,risk,.7f,null)),geometry);
            labelBounds(overlay,geometry,dp);Object entry=((Object[])entries(overlay))[0];
            Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas=new Canvas(bitmap);canvas.drawColor(background);overlay.draw(canvas);
                int actual=bitmap.getPixel(Math.round((float)field(entry,"labelX")+2*dp),
                    Math.round(((float)field(entry,"labelTop")+(float)field(entry,"labelBottom"))/2));
                double contrast=(luminance((int)field(entry,"color"))+.05)/(luminance(actual)+.05);
                if(contrast<4.5)throw new AssertionError("Label contrast below 4.5: "+risk+" / "+contrast);
                rows.put(new JSONObject().put("font_scale",font).put("risk",risk.name())
                    .put("camera_background",background==Color.WHITE?"white":"black").put("contrast",contrast));count++;
            } finally {bitmap.recycle();}
        }
        return count;
    }
    private static double luminance(int color) {
        double sum=0;int[] rgb={Color.red(color),Color.green(color),Color.blue(color)};double[] weights={.2126,.7152,.0722};
        for(int i=0;i<3;i++){double value=rgb[i]/255d;sum+=weights[i]*(value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4));}
        return sum;
    }
    private static Object field(Object host,String name) throws Exception {
        Field field=host.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(host);
    }
    private static String label(DetectionOverlay overlay) throws Exception {
        Object entry=((Object[])entries(overlay))[0];Field field=entry.getClass().getDeclaredField("label");field.setAccessible(true);
        return (String)field.get(entry);
    }
    private static void save(DetectionOverlay overlay,int w,int h,File file) throws IOException {
        Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        try(FileOutputStream stream=new FileOutputStream(file)) {
            Canvas canvas=new Canvas(image);canvas.drawColor(Color.rgb(18,46,41));overlay.draw(canvas);
            if(!image.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("Overlay evidence did not encode");
        }finally{image.recycle();}
    }

    private static Bitmap draw(DetectionOverlay view,int w,int h) {
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));return bitmap;
    }
    private static Object entries(DetectionOverlay overlay) {
        try {
            Field field=DetectionOverlay.class.getDeclaredField("entries");field.setAccessible(true);
            return field.get(overlay);
        } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
    }
    private static void assertEmpty(Bitmap bitmap) {
        int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        for(int pixel:pixels)if(pixel!=Color.TRANSPARENT)throw new AssertionError("Old overlay pixels survived clear");
    }
}
