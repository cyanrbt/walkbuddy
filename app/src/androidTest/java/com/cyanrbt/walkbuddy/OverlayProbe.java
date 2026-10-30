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
import java.util.ArrayList;
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
            Object drawing=field(overlay,"drawingEntries");
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
                if(drawing!=field(overlay,"drawingEntries"))throw new AssertionError("Identical snapshot rebuilt risk paint order");
                repeat=draw(overlay,w,h);
                if(!first.sameAs(repeat))throw new AssertionError("Cached redraw changed pixels");
                PreviewGeometry resized=new PreviewGeometry(640,480,0,0,w/2,h/2);
                overlay.show(data,resized);
                if(entries==entries(overlay))throw new AssertionError("Resize reused old coordinates");
                if(drawing==field(overlay,"drawingEntries"))throw new AssertionError("Resize kept the old risk drawing cache");
                overlay.show(data,geometry);
                overlay.clear();
                if(((Object[])field(overlay,"drawingEntries")).length!=0)throw new AssertionError("Clear retained risk drawing entries");
                Bitmap cleared=draw(overlay,w,h);
                try{assertEmpty(cleared);}finally{cleared.recycle();}
                overlay.show(data,geometry);
                overlay.show(data,null);
                if(((Object[])field(overlay,"drawingEntries")).length!=0)throw new AssertionError("Missing geometry retained risk drawing entries");
                Bitmap pending=draw(overlay,w,h);
                try{assertEmpty(pending);}finally{pending.recycle();}
            } finally {first.recycle();if(repeat!=null)repeat.recycle();}
        }
        checkLabels(runner,report);
        checkCrowdedLabels(runner,report);
        checkLabelLayering(runner,report);
        checkRiskPaint(runner,report);
        checkLabelMotion(runner,report);
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
    private static void checkCrowdedLabels(Instrumentation runner,StringBuilder report) throws Exception {
        File output=new File(runner.getTargetContext().getCacheDir(),"ui-probe");
        JSONArray scenes=new JSONArray();int cases=0,collisions=0;
        for(float font:new float[]{1f,1.5f,2f})for(int width:new int[]{96,160,320})for(String scene:new String[]{"row","mixed","dense","corners"}) {
            Configuration config=new Configuration(runner.getTargetContext().getResources().getConfiguration());
            config.fontScale=font;config.densityDpi=160;
            DetectionOverlay overlay=new DetectionOverlay(runner.getTargetContext().createConfigurationContext(config),null);
            int height=width*3/4+(scene.equals("corners")?80:0);overlay.layout(0,0,width,height);
            PreviewGeometry geometry=new PreviewGeometry(640,480,0,0,width,height);
            List<Detection> data=crowd(scene);overlay.show(data,geometry);
            JSONArray labels=new JSONArray();Object[] entries=(Object[])entries(overlay);
            int pairs=0,visible=0;
            for(int i=0;i<entries.length;i++) {
                Object entry=entries[i];String text=(String)field(entry,"label");
                float l=(float)field(entry,"labelX"),t=(float)field(entry,"labelTop");
                float r=l+(float)field(entry,"labelWidth")+8,b=(float)field(entry,"labelBottom");
                labels.put(new JSONObject().put("index",i).put("risk",data.get(i).risk.name()).put("text",text)
                    .put("left",l).put("top",t).put("right",r).put("bottom",b));
                if(text.isEmpty())continue;visible++;
                labelBounds(overlay,geometry,1,i);
                if(data.get(i).attention())qualifiedLabel(text,Float.isFinite(data.get(i).meters)?"约 0.7 米":"未测距");
                for(int j=0;j<i;j++) {
                    Object other=entries[j];if(((String)field(other,"label")).isEmpty())continue;
                    float ol=(float)field(other,"labelX"),ot=(float)field(other,"labelTop");
                    float or=ol+(float)field(other,"labelWidth")+8,ob=(float)field(other,"labelBottom");
                    if(l<or+2-.001f && r+2>ol+.001f && t<ob+2-.001f && b+2>ot+.001f)pairs++;
                }
            }
            String filename="crowd-font"+Math.round(font*100)+"-"+scene+"-"+width+".png";
            save(overlay,width,height,new File(output,filename));
            if(visible==0 || ((scene.equals("mixed") || scene.equals("dense"))
                && ((String)field(entries[entries.length-1],"label")).isEmpty()))
                throw new AssertionError("Crowded labels omitted the highest-priority target: "+scene+" / "+width+" / "+font);
            List<Detection> reversed=new ArrayList<>(data);Collections.reverse(reversed);overlay.show(reversed,geometry);
            Object[] reordered=(Object[])entries(overlay);
            for(int i=0;i<entries.length;i++) {
                Object a=entries[i],bEntry=reordered[entries.length-i-1];
                if(!field(a,"label").equals(field(bEntry,"label")) || !field(a,"labelX").equals(field(bEntry,"labelX"))
                    || !field(a,"labelTop").equals(field(bEntry,"labelTop")))
                    throw new AssertionError("Reordered detections moved their labels: "+scene);
            }
            overlay.show(reversed,geometry);
            if(reordered!=entries(overlay))throw new AssertionError("Repeated crowded snapshot rebuilt labels");
            scenes.put(new JSONObject().put("scene",scene).put("font_scale",font).put("width_dp",width)
                .put("height_dp",height).put("visible",visible).put("detections",data.size()).put("overlap_pairs",pairs)
                .put("labels",labels).put("image",filename));cases++;collisions+=pairs;
        }
        JSONObject result=new JSONObject().put("scope","Native constructed crowded labels; no real recognition")
            .put("source_behavior","Risk-prioritized bounded placement; shapes before labels").put("cases",cases).put("overlap_pairs",collisions).put("scenes",scenes);
        try(FileOutputStream stream=new FileOutputStream(new File(output,"overlay-crowd.json"))) {
            stream.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        if(collisions!=0)throw new AssertionError("Crowded label bubbles overlap or lack a 2dp gap: "+collisions);
        report.append("LABEL_CROWD_PASS cases=").append(cases).append(" overlap_pairs=").append(collisions)
            .append("; bounds, qualifiers, near-risk priority, reordered input, cache reuse\n");
    }
    private static void checkLabelLayering(Instrumentation runner,StringBuilder report) throws Exception {
        File output=new File(runner.getTargetContext().getCacheDir(),"ui-probe");
        for(float font:new float[]{1f,1.5f,2f}) {
            Configuration config=new Configuration(runner.getTargetContext().getResources().getConfiguration());
            config.fontScale=font;config.densityDpi=160;
            DetectionOverlay overlay=new DetectionOverlay(runner.getTargetContext().createConfigurationContext(config),null);
            overlay.layout(0,0,320,240);PreviewGeometry geometry=new PreviewGeometry(640,480,0,0,320,240);
            boolean[] pixels=new boolean[16*12];java.util.Arrays.fill(pixels,true);
            Detection label=new Detection("停车计时器",1,.2f,.6f,.8f,.9f,Detection.Risk.CAUTION,Float.NaN,null);
            Detection mask=new Detection("",1,0,0,1,1,Detection.Risk.CAPTURED,Float.NaN,new ObstacleMask(16,12,pixels));
            overlay.show(java.util.Arrays.asList(label,mask),geometry);
            Object entry=((Object[])entries(overlay))[0];
            int l=(int)(float)field(entry,"labelX"),t=(int)(float)field(entry,"labelTop");
            int r=(int)Math.ceil(l+(float)field(entry,"labelWidth")+8),b=(int)Math.ceil((float)field(entry,"labelBottom"));
            Bitmap first=draw(overlay,320,240),second=null;
            try {
                save(overlay,320,240,new File(output,"label-over-mask-font"+Math.round(font*100)+".png"));
                overlay.show(java.util.Arrays.asList(mask,label),geometry);second=draw(overlay,320,240);
                for(int y=t;y<b;y++)for(int x=l;x<r;x++)if(first.getPixel(x,y)!=second.getPixel(x,y))
                    throw new AssertionError("Later mask or outline repainted a label at "+x+","+y+" font="+font);
            } finally {first.recycle();if(second!=null)second.recycle();}
        }
        report.append("LABEL_LAYERING_PASS cases=3; native label pixels unchanged by later full-image mask\n");
    }
    private static void checkRiskPaint(Instrumentation runner,StringBuilder report) throws Exception {
        Configuration config=new Configuration(runner.getTargetContext().getResources().getConfiguration());
        config.fontScale=1;config.densityDpi=160;
        Context context=runner.getTargetContext().createConfigurationContext(config);
        File output=new File(runner.getTargetContext().getCacheDir(),"ui-probe");
        int background=Color.rgb(18,46,41),cases=0,mismatches=0;
        int[] colors={Color.rgb(174,183,197),Color.rgb(255,200,87),Color.rgb(255,99,110)};
        JSONArray rows=new JSONArray();
        for(String kind:new String[]{"near-outline","yellow-outline","mask-fill"}) {
            boolean fill=kind.equals("mask-fill");
            int expected=kind.equals("near-outline")?colors[2]:colors[1];
            if(fill) {
                Bitmap reference=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
                try {
                    Canvas canvas=new Canvas(reference);canvas.drawColor(background);
                    for(int color:colors)canvas.drawColor((color&0xffffff)|0x24000000);
                    expected=reference.getPixel(0,0);
                } finally {reference.recycle();}
            }
            boolean[] occupied=new boolean[16*12];java.util.Arrays.fill(occupied,true);
            ObstacleMask mask=fill?new ObstacleMask(16,12,occupied):null;
            Detection[] targets=new Detection[3];
            for(int i=0;i<3;i++)targets[i]=new Detection("空间障碍",.8f,
                fill?0:80.5f/320,fill?0:60.5f/240,fill?1:240.5f/320,fill?1:180.5f/240,
                Detection.Risk.values()[i],i==0?Float.NaN:i==1?1.4f:.7f,mask);
            int[][] permutations=kind.equals("yellow-outline")?new int[][]{{0,1},{1,0}}
                :new int[][]{{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
            for(int p=0;p<permutations.length;p++) {
                DetectionOverlay overlay=new DetectionOverlay(context,null);overlay.layout(0,0,320,240);
                List<Detection> data=new ArrayList<>();StringBuilder order=new StringBuilder();
                for(int i:permutations[p]){data.add(targets[i]);order.append(i);}
                overlay.show(data,new PreviewGeometry(640,480,0,0,320,240));
                int x=fill?160:80,y=fill?120:175;
                for(Object entry:(Object[])entries(overlay))if(!((String)field(entry,"label")).isEmpty()) {
                    float l=(float)field(entry,"labelX"),t=(float)field(entry,"labelTop");
                    if(x+.5f>=l && x+.5f<l+(float)field(entry,"labelWidth")+8 && y+.5f>=t && y+.5f<(float)field(entry,"labelBottom"))
                        throw new AssertionError("Risk pixel fixture intersects a label: "+kind+" / "+order);
                }
                Bitmap image=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);int actual;
                try {
                    Canvas canvas=new Canvas(image);canvas.drawColor(background);overlay.draw(canvas);actual=image.getPixel(x,y);
                } finally {image.recycle();}
                boolean matches=actual==expected;
                if(p==0 && !matches)throw new AssertionError("Reference risk order does not produce expected pixel: "+kind+" / "+Integer.toHexString(actual)+" / "+Integer.toHexString(expected));
                String filename="risk-"+kind+"-"+order+".png";save(overlay,320,240,new File(output,filename));
                rows.put(new JSONObject().put("kind",kind).put("order",order.toString()).put("x",x).put("y",y)
                    .put("expected_argb",String.format(java.util.Locale.ROOT,"%08x",expected))
                    .put("actual_argb",String.format(java.util.Locale.ROOT,"%08x",actual)).put("matches",matches).put("image",filename));
                cases++;if(!matches)mismatches++;
            }
        }
        JSONObject result=new JSONObject().put("scope","Native constructed risk layers with normal labels; sampled points verified outside label bubbles, not real recognition or tracking")
            .put("cases",cases).put("mismatches",mismatches).put("rows",rows);
        try(FileOutputStream stream=new FileOutputStream(new File(output,"risk-paint.json"))) {
            stream.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        if(mismatches!=0)throw new AssertionError("Lower-risk shape repainted a higher-risk color: "+mismatches);
        report.append("RISK_PAINT_PASS cases=").append(cases).append(" mismatches=").append(mismatches)
            .append("; risk-prioritized outlines/fills, labels retained, cached drawing order\n");
    }
    private static void checkLabelMotion(Instrumentation runner,StringBuilder report) throws Exception {
        File output=new File(runner.getTargetContext().getCacheDir(),"ui-probe");
        JSONArray sequences=new JSONArray();int frames=0,switches=0;
        for(float font:new float[]{1f,1.5f,2f})for(int width:new int[]{160,320}) {
            Configuration config=new Configuration(runner.getTargetContext().getResources().getConfiguration());
            config.fontScale=font;config.densityDpi=160;
            DetectionOverlay overlay=new DetectionOverlay(runner.getTargetContext().createConfigurationContext(config),null);
            int height=width*3/4;overlay.layout(0,0,width,height);
            PreviewGeometry geometry=new PreviewGeometry(640,480,0,0,width,height);
            float[] lastTop={Float.NaN,Float.NaN};JSONArray snapshots=new JSONArray();int changes=0;
            for(int frame=0;frame<8;frame++) {
                List<Detection> data=motionFrame(frame);overlay.show(data,geometry);
                Object[] entries=(Object[])entries(overlay);JSONArray labels=new JSONArray();
                for(int i=0;i<entries.length;i++) {
                    Object entry=entries[i];Detection d=data.get(i);String shown=(String)field(entry,"label");
                    if(shown.isEmpty())throw new AssertionError("Motion fixture requires both labels: "+font+" / "+width);
                    labelBounds(overlay,geometry,1,i);qualifiedLabel(shown,"未测距");
                    int key=d.label.equals("停车计时器")?0:1;float top=(float)field(entry,"labelTop");
                    if(Float.isFinite(lastTop[key]) && Math.abs(top-lastTop[key])>8)changes++;
                    lastTop[key]=top;
                    labels.put(new JSONObject().put("name",d.label).put("score",d.score).put("text",shown)
                        .put("object_left",d.left*width).put("object_top",d.top*height)
                        .put("label_left",(float)field(entry,"labelX")).put("label_top",top)
                        .put("label_right",(float)field(entry,"labelX")+(float)field(entry,"labelWidth")+8)
                        .put("label_bottom",(float)field(entry,"labelBottom")));
                }
                String filename="motion-font"+Math.round(font*100)+"-"+width+"-frame"+frame+".png";
                save(overlay,width,height,new File(output,filename));
                snapshots.put(new JSONObject().put("frame",frame).put("labels",labels).put("image",filename));frames++;
            }
            sequences.put(new JSONObject().put("font_scale",font).put("width_dp",width).put("height_dp",height)
                .put("frames",snapshots).put("vertical_switches",changes));switches+=changes;
        }
        JSONObject result=new JSONObject().put("scope","Native constructed score-flip sequence with slow horizontal motion; no real object tracking")
            .put("frames",frames).put("vertical_switches",switches).put("sequences",sequences);
        try(FileOutputStream stream=new FileOutputStream(new File(output,"label-motion-baseline.json"))) {
            stream.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        report.append("LABEL_MOTION_BASELINE frames=").append(frames).append(" vertical_switches=").append(switches).append('\n');
    }
    private static List<Detection> motionFrame(int frame) {
        List<Detection> data=new ArrayList<>();float shift=frame*.002f;
        data.add(new Detection("停车计时器",frame%2==0?.81f:.79f,.18f+shift,.38f,.82f+shift,.9f,Detection.Risk.CAUTION,Float.NaN,null));
        data.add(new Detection("交通信号灯",frame%2==0?.79f:.81f,.18f+shift,.38f,.82f+shift,.9f,Detection.Risk.CAUTION,Float.NaN,null));
        if(frame%2!=0)Collections.reverse(data);
        return data;
    }
    private static List<Detection> crowd(String scene) {
        List<Detection> data=new ArrayList<>();
        int count=scene.equals("dense")?31:scene.equals("mixed")?8:4;
        for(int i=0;i<count;i++) {
            float l=.03f+i*.22f,t=.28f,r=Math.min(1,l+.3f),b=.9f;
            if(scene.equals("dense") || scene.equals("mixed")){l=.12f;t=.3f;r=.88f;b=.85f;}
            if(scene.equals("corners")){l=(i%2)*.75f;t=(i/2)*.75f;r=l+.25f;b=t+.25f;}
            Detection.Risk risk=scene.equals("row") || scene.equals("corners")?Detection.Risk.CAUTION
                :i==count-1?Detection.Risk.NEAR:i%3==1?Detection.Risk.CAUTION:Detection.Risk.CAPTURED;
            data.add(new Detection(i==count-1?"空间障碍":"停车计时器",.6f+i*.01f,l,t,r,b,risk,
                risk==Detection.Risk.NEAR?.7f:Float.NaN,null));
        }
        return data;
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
        labelBounds(overlay,geometry,dp,0);
    }
    private static void labelBounds(DetectionOverlay overlay,PreviewGeometry geometry,float dp,int index) throws Exception {
        Object entry=((Object[])entries(overlay))[index];String shown=(String)field(entry,"label");
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
