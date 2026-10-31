package com.cyanrbt.walkbuddy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.opengl.*;
import android.os.*;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Independent frozen producers, not PreviewActivity safety or real-AR acceptance. */
final class SurfaceProducerProbe {
    static JSONObject run(Instrumentation runner,Activity host) throws Exception {
        JSONObject camera,egl;
        CameraTimeline timeline=new CameraTimeline();
        try{camera=camera(runner,host,timeline);}catch(Throwable error){camera=new JSONObject().put("accepted",false).put("error",error.toString());}
        camera.put("stateEvents",timeline.snapshot());
        try{egl=egl(runner,host);}catch(Throwable error){egl=new JSONObject().put("accepted",false).put("error",error.toString());}
        return new JSONObject().put("scope","Frozen virtual Camera2 and synthetic EGL; no product lifecycle, actual AR, or device acceptance")
            .put("camera",camera).put("egl",egl).put("accepted",camera.getBoolean("accepted") && egl.getBoolean("accepted"));
    }

    private static JSONObject camera(Instrumentation runner,Activity host,CameraTimeline timeline) throws Exception {
        CameraManager manager=(CameraManager)host.getSystemService(Context.CAMERA_SERVICE);
        String selected=null;CameraCharacteristics characteristics=null;
        for(String id:manager.getCameraIdList()) {
            CameraCharacteristics c=manager.getCameraCharacteristics(id);
            StreamConfigurationMap streams=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(c.get(CameraCharacteristics.LENS_FACING))
                && streams!=null && Arrays.asList(streams.getOutputSizes(SurfaceTexture.class)).contains(new Size(640,480))) {
                selected=id;characteristics=c;break;
            }
        }
        if(selected==null)throw new AssertionError("No matching virtual rear Camera2 producer");
        int sensor=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean disableCrop=false;
        if(Build.VERSION.SDK_INT>=31){int[] modes=characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES);
            if(modes!=null)for(int mode:modes)if(mode==CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)disableCrop=true;}
        final boolean cropNone=disableCrop;
        AtomicReference<CameraDevice> device=new AtomicReference<>();AtomicReference<CameraCaptureSession> session=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();AtomicBoolean closed=new AtomicBoolean(),stopping=new AtomicBoolean();
        AtomicInteger captures=new AtomicInteger();AtomicLong lastStamp=new AtomicLong();
        CountDownLatch frames=new CountDownLatch(3),idle=new CountDownLatch(1),sequenceDone=new CountDownLatch(1);
        Fixture fixture=new Fixture(runner,host,640,480,sensor);
        try {
            manager.openCamera(selected,new CameraDevice.StateCallback(){
                private void failed(String message){if(!closed.get()){timeline.event("ERROR "+message,stopping.get());error.set(message);while(frames.getCount()>0)frames.countDown();idle.countDown();sequenceDone.countDown();}}
                @Override public void onOpened(CameraDevice camera) {
                    timeline.event("OPENED",stopping.get());
                    if(closed.get()){camera.close();return;}device.set(camera);
                    try{camera.createCaptureSession(Collections.singletonList(fixture.surface),new CameraCaptureSession.StateCallback(){
                        @Override public void onConfigured(CameraCaptureSession ready) {
                            timeline.event("CONFIGURED",stopping.get());
                            if(closed.get()){ready.close();return;}session.set(ready);
                            try{CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(fixture.surface);
                                request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                if(Build.VERSION.SDK_INT>=31 && cropNone)request.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_NONE);
                                int repeatingId=ready.setRepeatingRequest(request.build(),new CameraCaptureSession.CaptureCallback(){
                                    @Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result) {
                                        if(closed.get())return;Long stamp=result.get(CaptureResult.SENSOR_TIMESTAMP);
                                        if(stamp!=null){lastStamp.set(stamp);int count=captures.incrementAndGet();if(count<=4)timeline.event("CAPTURE count="+count+" frame="+result.getFrameNumber(),stopping.get());frames.countDown();}
                                    }
                                    @Override public void onCaptureSequenceCompleted(CameraCaptureSession s,int id,long frame){timeline.event("SEQUENCE_COMPLETED id="+id+" frame="+frame,stopping.get());if(stopping.get())sequenceDone.countDown();}
                                    @Override public void onCaptureSequenceAborted(CameraCaptureSession s,int id){timeline.event("SEQUENCE_ABORTED id="+id,stopping.get());}
                                },new Handler(Looper.getMainLooper()));
                                timeline.event("REPEATING_SUBMITTED id="+repeatingId,stopping.get());
                            }catch(Exception failure){failed(failure.toString());}
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s){s.close();failed("Camera2 configuration failed");}
                        @Override public void onReady(CameraCaptureSession s){timeline.state("READY",true,stopping.get());if(stopping.get())idle.countDown();}
                        @Override public void onActive(CameraCaptureSession s){timeline.state("ACTIVE",false,stopping.get());}
                        @Override public void onClosed(CameraCaptureSession s){timeline.event("SESSION_CLOSED",stopping.get());}
                    },new Handler(Looper.getMainLooper()));}catch(Exception failure){failed(failure.toString());}
                }
                @Override public void onDisconnected(CameraDevice camera){camera.close();failed("Camera2 disconnected");}
                @Override public void onError(CameraDevice camera,int code){camera.close();failed("Camera2 error="+code);}
            },new Handler(Looper.getMainLooper()));
            require(frames,8,"Camera2 frames");if(error.get()!=null)throw new AssertionError(error.get());
            SurfaceCopyProbe.onMain(runner,()->{stopping.set(true);timeline.event("STOP_REPEATING",true);session.get().stopRepeating();timeline.event("STOP_RETURNED",true);return null;});
            require(sequenceDone,5,"Camera2 repeating sequence completed");
            timeline.event("SEQUENCE_WAIT_PASSED",stopping.get());
            require(idle,5,"Camera2 idle after explicit stopRepeating");
            timeline.event("IDLE_WAIT_PASSED",stopping.get());
            if(error.get()!=null)throw new AssertionError(error.get());
            fixture.settle();
            JSONObject result=fixture.pairs().put("sensor",sensor).put("metadataFrames",captures.get())
                .put("lastSensorTimestamp",lastStamp.get()).put("rotateAndCropNone",cropNone);
            return result;
        } finally {
            timeline.event("FINISH captures="+captures.get(),stopping.get());
            closed.set(true);
            try{SurfaceCopyProbe.onMain(runner,()->{if(session.get()!=null)session.get().close();if(device.get()!=null)device.get().close();return null;});}
            finally{fixture.close();}
        }
    }

    /** Observe the original gate, including failures; this recorder never satisfies a latch. */
    private static final class CameraTimeline {
        private final List<Map<String,Object>> events=new ArrayList<>();
        private Boolean ready;
        synchronized void state(String name,boolean value,boolean stopping){ready=value;event(name,stopping);}
        synchronized void event(String name,boolean stopping){
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("event",name);row.put("elapsedRealtimeNs",SystemClock.elapsedRealtimeNanos());
            row.put("onMain",Looper.myLooper()==Looper.getMainLooper());row.put("stopping",stopping);row.put("lastReady",ready);
            events.add(row);
        }
        synchronized JSONArray snapshot(){return new JSONArray(new ArrayList<>(events));}
    }

    private static JSONObject egl(Instrumentation runner,Activity host) throws Exception {
        try(Fixture fixture=new Fixture(runner,host,480,640,90);EglProducer producer=new EglProducer(fixture.surface)) {
            producer.paint();fixture.settle();return fixture.pairs();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Instrumentation runner;final TextureView view;final Surface surface;
        final int bufferWidth,bufferHeight,sensor;final FrameLayout root;
        Fixture(Instrumentation runner,Activity host,int bufferWidth,int bufferHeight,int sensor) throws Exception {
            this.runner=runner;this.bufferWidth=bufferWidth;this.bufferHeight=bufferHeight;this.sensor=sensor;
            CountDownLatch available=new CountDownLatch(1);AtomicReference<TextureView> texture=new AtomicReference<>();
            root=SurfaceCopyProbe.onMain(runner,()->{
                FrameLayout frame=new FrameLayout(host);TextureView v=new TextureView(host);v.setOpaque(false);
                v.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
                    public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){s.setDefaultBufferSize(bufferWidth,bufferHeight);transform(v,w,h);available.countDown();}
                    public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){transform(v,w,h);}
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture s){return true;}
                    public void onSurfaceTextureUpdated(SurfaceTexture s){}
                });frame.addView(v,new FrameLayout.LayoutParams(300,400));host.setContentView(frame);texture.set(v);return frame;
            });
            require(available,5,"Producer TextureView available");view=texture.get();
            surface=SurfaceCopyProbe.onMain(runner,()->new Surface(view.getSurfaceTexture()));
        }
        private void transform(TextureView target,int w,int h){PreviewGeometry geometry=new PreviewGeometry(640,480,sensor,0,w,h);
            Matrix m=new Matrix();m.setValues(geometry.matrix);target.setTransform(m);}
        void settle() throws Exception {CountDownLatch drawn=new CountDownLatch(1);
            SurfaceCopyProbe.onMain(runner,()->{view.postOnAnimation(()->view.postOnAnimation(drawn::countDown));return null;});require(drawn,5,"Frozen producer drawn");}
        JSONObject pairs() throws Exception {
            JSONArray pairs=new JSONArray();boolean accepted=true;
            for(int[] size:new int[][]{{300,400},{280,210}}) {
                SurfaceCopyProbe.onMain(runner,()->{view.setLayoutParams(new FrameLayout.LayoutParams(size[0],size[1]));return null;});settle();
                SurfaceCopyProbe.onMain(runner,()->{BitmapWarmup.read(view);return null;});
                long before=SurfaceCopyProbe.onMain(runner,()->view.getSurfaceTexture().getTimestamp());
                JSONObject result=SurfaceCopyProbe.compare(runner,view,sensor,0,size[0],size[1]);
                BrightnessReadbackProbe.verifyActualCopy(runner,view);
                long after=SurfaceCopyProbe.onMain(runner,()->view.getSurfaceTexture().getTimestamp());
                boolean stable=before>0 && before==after;
                result.put("sourceTimestampBefore",before).put("sourceTimestampAfter",after).put("sourceTimestampStable",stable)
                    .put("asyncReaderVerified",true);
                accepted&=result.getBoolean("accepted") && stable;pairs.put(result);
            }
            return new JSONObject().put("accepted",accepted).put("pairs",pairs);
        }
        @Override public void close(){SurfaceCopyProbe.onMain(runner,()->{surface.release();view.setSurfaceTextureListener(null);root.removeAllViews();return null;});}
    }
    private static final class BitmapWarmup {static void read(TextureView view){android.graphics.Bitmap b=view.getBitmap(16,12);if(b==null)throw new AssertionError("Frozen source missing");b.recycle();}}
    private static void require(CountDownLatch signal,int seconds,String label) throws InterruptedException {
        if(!signal.await(seconds,TimeUnit.SECONDS))throw new AssertionError("Timed out: "+label);
    }

    private static final class EglProducer implements AutoCloseable {
        final HandlerThread thread=new HandlerThread("Surface copy EGL");final Handler work;
        final Surface target;EGLDisplay display=EGL14.EGL_NO_DISPLAY;EGLContext context=EGL14.EGL_NO_CONTEXT;EGLSurface window=EGL14.EGL_NO_SURFACE;
        EglProducer(Surface target){this.target=target;thread.start();work=new Handler(thread.getLooper());}
        void paint() throws Exception {
            CountDownLatch ready=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
            work.post(()->{try{
                display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] version=new int[2];
                if(!EGL14.eglInitialize(display,version,0,version,1))throw new AssertionError("EGL initialize");
                EGLConfig[] config=new EGLConfig[1];int[] count=new int[1];
                EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,config,0,1,count,0);
                if(count[0]!=1)throw new AssertionError("EGL config");
                context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
                window=EGL14.eglCreateWindowSurface(display,config[0],target,new int[]{EGL14.EGL_NONE},0);
                if(!EGL14.eglMakeCurrent(display,window,window,context))throw new AssertionError("EGL current");
                GLES20.glViewport(0,0,480,640);GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
                fill(0,0,240,320,1,0,0,1);fill(240,0,240,320,0,.5f,0,.5f);
                fill(0,320,240,320,0,0,.25f,.25f);fill(240,320,240,320,1,1,1,1);
                fill(200,280,80,80,3/255f,3/255f,3/255f,1);
                if(GLES20.glGetError()!=GLES20.GL_NO_ERROR || !EGL14.eglSwapBuffers(display,window))throw new AssertionError("EGL frozen frame");
            }catch(Throwable error){failure.set(error);}finally{ready.countDown();}});
            require(ready,8,"EGL producer paint");if(failure.get()!=null)throw new AssertionError(failure.get());
        }
        private static void fill(int x,int y,int w,int h,float r,float g,float b,float a){GLES20.glScissor(x,y,w,h);GLES20.glClearColor(r,g,b,a);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);}
        @Override public void close() throws Exception {
            CountDownLatch done=new CountDownLatch(1);
            work.post(()->{try{if(display!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);}}
                finally{done.countDown();}});
            try{require(done,5,"EGL release");}finally{thread.quitSafely();thread.join(2000);}
            if(thread.isAlive())throw new AssertionError("EGL producer thread did not close");
        }
    }
}
