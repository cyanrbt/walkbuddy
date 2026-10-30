package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.opengl.*;
import android.os.*;
import android.view.Surface;
import com.google.ar.core.*;
import java.nio.*;
import java.util.*;

/** Owns ARCore and EGL on one process-wide serial thread. Never opens alongside Camera2. */
final class ArDepthCamera {
    interface Callback {void frame(long timestamp,long capturedAt,DepthSnapshot depth);void failed(String reason);}
    private static final HandlerThread THREAD=new HandlerThread("WalkBuddy depth camera");
    private static final Handler WORK;
    static {THREAD.start();WORK=new Handler(THREAD.getLooper());}
    private final Context context;
    private final SurfaceTexture target;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile Callback callback;
    private volatile boolean stopped;
    private Session session;
    private Surface surface;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext glContext=EGL14.EGL_NO_CONTEXT;
    private EGLSurface window=EGL14.EGL_NO_SURFACE;
    private int texture,program;
    private long lastFrame,lastSample,lastDepthStamp;
    private float floorY=Float.NaN;
    private long floorSince;
    private DepthSnapshot latest;
    private DepthProjection projection; // Created and used only on WORK after fresh depth is available.
    private final FloatBuffer vertices=buffer(new float[]{-1,-1,1,-1,-1,1,1,1});
    private final FloatBuffer uv=buffer(new float[8]);
    ArDepthCamera(Context context,SurfaceTexture target,Callback callback){this.context=context.getApplicationContext();this.target=target;this.callback=callback;}
    void start(){WORK.post(()->{try{if(stopped)return;open();tick();}catch(Exception|LinkageError error){failure();}});}
    void stop(){stopped=true;callback=null;WORK.post(this::release);}
    static void afterRelease(Runnable action){WORK.post(()->new Handler(Looper.getMainLooper()).post(action));}
    private void open() throws Exception {
        session=new Session(context);
        if(!session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))throw new IllegalStateException("Depth unavailable");
        Config config=new Config(session);config.setDepthMode(Config.DepthMode.AUTOMATIC);
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);config.setFocusMode(Config.FocusMode.AUTO);
        session.configure(config);session.setDisplayGeometry(Surface.ROTATION_0,480,640);
        target.setDefaultBufferSize(480,640);surface=new Surface(target);
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2];if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("EGL init");
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,configs,0,1,count,0);
        if(count[0]==0)throw new IllegalStateException("EGL config");
        glContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        window=EGL14.eglCreateWindowSurface(display,configs[0],surface,new int[]{EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(display,window,window,glContext))throw new IllegalStateException("EGL context");
        int[] textures=new int[1];GLES20.glGenTextures(1,textures,0);texture=textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        int vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p; attribute vec2 t; varying vec2 v; void main(){gl_Position=vec4(p,0.,1.);v=t;}");
        int fragment=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES camera; varying vec2 v; void main(){gl_FragColor=texture2D(camera,v);}");
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);if(linked[0]==0)throw new IllegalStateException("Shader link");
        session.setCameraTextureName(texture);session.resume();
    }
    private void tick() {
        if(stopped){release();return;}
        try {
            Frame frame=session.update();long now=SystemClock.elapsedRealtime();
            if(frame.getTimestamp()!=0 && frame.getTimestamp()!=lastFrame) {
                lastFrame=frame.getTimestamp();render(frame);
                if(now-lastSample>=200){lastSample=now;latest=depth(frame,now);}
                DepthSnapshot snapshot=latest;long timestamp=lastFrame;
                main.post(()->{Callback c=callback;if(!stopped && c!=null)c.frame(timestamp,now,snapshot);});
            }
            WORK.postDelayed(this::tick,16);
        }catch(Exception|LinkageError error){failure();}
    }
    private void render(Frame frame) {
        vertices.rewind();uv.rewind();frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,vertices,Coordinates2d.TEXTURE_NORMALIZED,uv);
        GLES20.glViewport(0,0,480,640);GLES20.glUseProgram(program);
        int p=GLES20.glGetAttribLocation(program,"p"),t=GLES20.glGetAttribLocation(program,"t");
        vertices.rewind();uv.rewind();GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glVertexAttribPointer(t,2,GLES20.GL_FLOAT,false,0,uv);
        GLES20.glEnableVertexAttribArray(p);GLES20.glEnableVertexAttribArray(t);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"camera"),0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        if(!EGL14.eglSwapBuffers(display,window))throw new IllegalStateException("Display lost");
    }
    private DepthSnapshot depth(Frame frame,long now) {
        if(frame.getCamera().getTrackingState()!=TrackingState.TRACKING){floorY=Float.NaN;floorSince=0;return empty(now,"测距未就绪 · 请缓慢移动并看向前方地面",DepthSnapshot.Stage.TRACKING);}
        Pose pose=frame.getCamera().getPose();float[] forward=pose.getZAxis();float fx=-forward[0],fz=-forward[2];float length=(float)Math.hypot(fx,fz);
        if(length<0.5f){floorY=Float.NaN;floorSince=0;return empty(now,"请调整姿势，让镜头朝向前方",DepthSnapshot.Stage.ORIENT);}fx/=length;fz/=length;
        float found=Float.NaN;
        for(Plane plane:session.getAllTrackables(Plane.class)) {
            if(plane.getTrackingState()!=TrackingState.TRACKING || plane.getSubsumedBy()!=null || plane.getType()!=Plane.Type.HORIZONTAL_UPWARD_FACING)continue;
            float y=plane.getCenterPose().ty(),height=pose.ty()-y;
            if(height<DepthThresholds.GROUND_PHONE_HEIGHT_MIN || height>DepthThresholds.GROUND_PHONE_HEIGHT_MAX
                || plane.getExtentX()*plane.getExtentZ()<DepthThresholds.GROUND_MIN_AREA)continue;
            if(!plane.isPoseInPolygon(Pose.makeTranslation(pose.tx()+fx*0.5f,y,pose.tz()+fz*0.5f)))continue;
            if(Float.isNaN(found) || y<found)found=y;
        }
        boolean groundSearching=Float.isNaN(found);
        if(groundSearching){floorY=Float.NaN;floorSince=0;}
        else if(Float.isNaN(floorY) || Math.abs(found-floorY)>0.08f){floorY=found;floorSince=now;}
        else floorY=found;
        boolean groundKnown=Float.isFinite(floorY) && now-floorSince>=1000;
        DepthSnapshot.Stage preDepth=groundSearching?DepthSnapshot.Stage.GROUND_SEARCH
            :groundKnown?DepthSnapshot.Stage.DEPTH_WAIT:DepthSnapshot.Stage.GROUND_HOLD;
        try(Image depth=frame.acquireRawDepthImage16Bits();Image confidence=frame.acquireRawDepthConfidenceImage()) {
            if(depth.getWidth()!=confidence.getWidth() || depth.getHeight()!=confidence.getHeight())return empty(now,"深度格式不可用",DepthSnapshot.Stage.DEPTH_STALE);
            long age=frame.getTimestamp()-depth.getTimestamp();
            DepthTiming.State timing=DepthTiming.check(frame.getTimestamp(),depth.getTimestamp());
            if(timing!=DepthTiming.State.FRESH)return empty(now,timing==DepthTiming.State.EXPIRED?"深度更新较慢 · 暂停测距":timing==DepthTiming.State.FUTURE_TIMESTAMP?"深度时间不同步 · 暂停测距":"深度时间缺失 · 暂停测距",DepthSnapshot.Stage.DEPTH_STALE);
            // The confidence API returns the matching image for this Frame; its timestamp is not an age contract.
            if(depth.getTimestamp()==lastDepthStamp)return groundKnown && latest!=null && now-latest.sampledAt<=500?latest:empty(now,"等待新的深度观测",preDepth);
            lastDepthStamp=depth.getTimestamp();
            int w=DepthObstacles.WIDTH,h=DepthObstacles.HEIGHT,n=w*h;
            if(projection==null)projection=new DepthProjection(w,h);
            projection.reset();
            float[] textureCoords=projection.textureCoords;
            frame.transformCoordinates2d(Coordinates2d.VIEW_NORMALIZED,projection.view,Coordinates2d.TEXTURE_NORMALIZED,textureCoords);
            frame.transformCoordinates2d(Coordinates2d.VIEW_NORMALIZED,projection.view,Coordinates2d.IMAGE_PIXELS,projection.imageCoords);
            CameraIntrinsics intrinsics=frame.getCamera().getImageIntrinsics();
            intrinsics.getFocalLength(projection.focal,0);intrinsics.getPrincipalPoint(projection.center,0);
            Image.Plane dp=depth.getPlanes()[0],cp=confidence.getPlanes()[0];ByteBuffer db=dp.getBuffer().order(ByteOrder.nativeOrder()),cb=cp.getBuffer();
            DepthObstacles.Grid grid=new DepthObstacles.Grid(w,h,groundKnown);int valid=0;
            for(int i=0;i<n;i++) {
                if(!projection.mapped(i))continue;
                float u=textureCoords[2*i],v=textureCoords[2*i+1];
                int x=(int)(u*depth.getWidth()),y=(int)(v*depth.getHeight());
                int dIndex=y*dp.getRowStride()+x*dp.getPixelStride(),cIndex=y*cp.getRowStride()+x*cp.getPixelStride();
                if(dIndex+1>=db.limit() || cIndex>=cb.limit())continue;
                float z=(db.getShort(dIndex)&65535)/1000f,c=(cb.get(cIndex)&255)/255f;if(z<0.2f || z>5 || c<0.65f)continue;
                projection.project(pose,i,z,fx,fz,floorY,grid);grid.confidence[i]=c;valid++;
            }
            if(valid<DepthThresholds.MIN_VALID_DEPTH_SAMPLES)return empty(now,"深度覆盖不足 · 未测到不代表可通行",DepthSnapshot.Stage.DEPTH_SPARSE);
            if(!groundKnown)return new DepthSnapshot(now-age/1000000L,DepthObstacles.detect(grid),
                "正在确认地面 · 暂不判断碰撞距离",DepthSnapshot.Stage.GROUND_HOLD,false,true);
            List<Detection> combined=new ArrayList<>(DepthObstacles.detect(grid));
            combined.addAll(DepthDrops.detect(grid));
            return new DepthSnapshot(now-age/1000000L,combined,
                "深度增强 · 按空间占用提示，无需认识物体",DepthSnapshot.Stage.MEASURING,true,true);
        }catch(com.google.ar.core.exceptions.NotYetAvailableException e){return empty(now,"等待深度 · 缓慢移动手机以建立环境",DepthSnapshot.Stage.DEPTH_WAIT);}
    }
    private static DepthSnapshot empty(long now,String message,DepthSnapshot.Stage stage){
        return new DepthSnapshot(now,Collections.emptyList(),message,stage,false,false);
    }
    private void failure(){if(stopped)return;Callback failedCallback=callback;main.post(()->{if(callback==failedCallback && failedCallback!=null)failedCallback.failed("增强测距暂不可用，已停止；可重新开启普通预览");});stopped=true;release();}
    private void release() {
        projection=null;
        if(session!=null){try{session.pause();}catch(RuntimeException ignored){}try{session.close();}catch(RuntimeException ignored){}session=null;}
        if(display!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);if(glContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,glContext);EGL14.eglTerminate(display);}
        display=EGL14.EGL_NO_DISPLAY;window=EGL14.EGL_NO_SURFACE;glContext=EGL14.EGL_NO_CONTEXT;
        if(surface!=null){surface.release();surface=null;}
    }
    private static FloatBuffer buffer(float[] values){FloatBuffer out=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();out.put(values).rewind();return out;}
    private static int shader(int type,String source){int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException("Shader compile");return shader;}
}
