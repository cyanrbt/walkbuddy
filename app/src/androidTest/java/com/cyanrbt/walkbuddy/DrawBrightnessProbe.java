package com.cyanrbt.walkbuddy;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Actual update-callback submission after one warm frame; no getBitmap inside drawing. */
final class DrawBrightnessProbe {
    static String run(Instrumentation runner,Activity host) throws Exception {
        CountDownLatch available=new CountDownLatch(1),warm=new CountDownLatch(1),completed=new CountDownLatch(1);
        AtomicReference<TextureView> view=new AtomicReference<>();AtomicReference<BrightnessReadback> reader=new AtomicReference<>();
        AtomicReference<int[]> copied=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        AtomicBoolean armed=new AtomicBoolean(),requested=new AtomicBoolean(),offMain=new AtomicBoolean();AtomicInteger updates=new AtomicInteger();
        FrameLayout root=SurfaceCopyProbe.onMain(runner,()->{
            FrameLayout frame=new FrameLayout(host);TextureView texture=new TextureView(host);view.set(texture);
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
                @Override public void onSurfaceTextureAvailable(SurfaceTexture source,int w,int h){source.setDefaultBufferSize(320,240);available.countDown();}
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture source,int w,int h){}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture source){return true;}
                @Override public void onSurfaceTextureUpdated(SurfaceTexture source){
                    updates.incrementAndGet();warm.countDown();
                    if(!armed.get() || !requested.compareAndSet(false,true))return;
                    try {
                        BrightnessReadback copy=new BrightnessReadback((owner,expired,status,total)->{
                            try {
                                if(Looper.myLooper()!=Looper.getMainLooper() || expired || status!=PixelCopy.SUCCESS)
                                    throw new AssertionError("Draw-submitted copy failed: expired="+expired+" status="+status);
                                Field storage=BrightnessReadback.class.getDeclaredField("bitmap");storage.setAccessible(true);
                                Bitmap bitmap=(Bitmap)storage.get(owner);int[] pixels=new int[192];bitmap.getPixels(pixels,0,16,0,0,16,12);copied.set(pixels);
                                if(total!=73440)throw new AssertionError("Copied warm/partial frame instead of second patterned buffer: "+total);
                            }catch(Throwable error){failure.compareAndSet(null,error);}finally{completed.countDown();}
                        },SystemClock::elapsedRealtime,(surface,target,done,handler)->{
                            offMain.set(Looper.myLooper()!=Looper.getMainLooper());PixelCopy.request(surface,target,done::accept,handler);
                        });
                        reader.set(copy);long now=SystemClock.elapsedRealtime();copy.observe(now);
                        if(!copy.request(texture,now))throw new AssertionError("Draw callback request rejected");
                    }catch(Throwable error){failure.compareAndSet(null,error);completed.countDown();}
                }
            });frame.addView(texture,new FrameLayout.LayoutParams(200,150));host.setContentView(frame);return frame;
        });
        Surface surface=null;
        try {
            require(available,"draw TextureView available");surface=SurfaceCopyProbe.onMain(runner,()->new Surface(view.get().getSurfaceTexture()));
            paint(surface,false);require(warm,"first warm buffer");settle(runner,view.get());
            SurfaceCopyProbe.onMain(runner,()->{armed.set(true);return null;});paint(surface,true);
            require(completed,"copy submitted during update callback");
            if(failure.get()!=null)throw new AssertionError(failure.get());
            int[] reference=SurfaceCopyProbe.onMain(runner,()->SurfaceCopyProbe.reference(view.get()));
            if(!offMain.get() || updates.get()<2 || !Arrays.equals(reference,copied.get()))
                throw new AssertionError("Draw submission changed actual frozen pixels or thread");
            return "BRIGHTNESS_DRAW_SUBMISSION_PASS frames=2 during_update=true copy_off_main=true exact_argb=true second_buffer_rgb=73440\n";
        } finally {
            Surface source=surface;
            SurfaceCopyProbe.onMain(runner,()->{if(reader.get()!=null)reader.get().close();if(source!=null)source.release();
                view.get().setSurfaceTextureListener(null);root.removeAllViews();return null;});
        }
    }
    private static void paint(Surface surface,boolean patterned){
        Canvas canvas=surface.lockCanvas(null);
        try{canvas.drawColor(Color.WHITE);if(patterned){Paint paint=new Paint();
            paint.setColor(Color.RED);canvas.drawRect(0,0,160,120,paint);
            paint.setColor(Color.GREEN);canvas.drawRect(160,0,320,120,paint);
            paint.setColor(Color.BLUE);canvas.drawRect(0,120,160,240,paint);
        }}finally{surface.unlockCanvasAndPost(canvas);}
    }
    private static void settle(Instrumentation runner,TextureView view) throws Exception {
        CountDownLatch drawn=new CountDownLatch(1);
        SurfaceCopyProbe.onMain(runner,()->{view.postOnAnimation(()->view.postOnAnimation(drawn::countDown));return null;});require(drawn,"warm frame drawn");
    }
    private static void require(CountDownLatch latch,String label) throws InterruptedException {
        if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("Timed out: "+label);
    }
}
