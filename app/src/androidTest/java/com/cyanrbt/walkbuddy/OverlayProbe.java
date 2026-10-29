package com.cyanrbt.walkbuddy;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

/** Camera-free raster regression for masks, dash gaps, redraw reuse, resize and clear. */
final class OverlayProbe {
    static void run(Instrumentation runner) {
        Bundle result=new Bundle();
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("Overlay probe is restricted to emulators");
            runner.runOnMainSync(()->check(runner));
            result.putString("stream","OVERLAY_PROBE_PASS: yellow depth dashes, solid gray/red, cached redraw, resize, clear, narrow label");
            runner.finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream","OVERLAY_PROBE_FAIL: "+error);
            runner.finish(Activity.RESULT_CANCELED,result);
        }
    }

    private static void check(Instrumentation runner) {
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
        // A narrow PiP image must not send a negative label width to ellipsize.
        overlay.layout(0,0,1,1);
        overlay.show(Collections.singletonList(new Detection("很长的标签",1,0,0,1,1)),
            new PreviewGeometry(640,480,0,0,1,1));
        Bitmap tiny=draw(overlay,1,1);tiny.recycle();
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
