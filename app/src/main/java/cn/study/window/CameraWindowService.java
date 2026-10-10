package cn.study.window;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.display.DisplayManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class CameraWindowService extends Service {
    private static volatile String publicReport = "已停止；请主动开启";
    public static String report() { return publicReport; }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SessionState state = new SessionState();
    private final AlertPolicy policy = new AlertPolicy();
    private ToneGenerator activeTone;
    private WindowManager wm;
    private WindowManager.LayoutParams layout;
    private LinearLayout root;
    private TextureView texture;
    private TextView cover, info, titleView;
    private CheckBox recognition, sound;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private Surface surface;
    private static final int BUFFER_WIDTH=640,BUFFER_HEIGHT=480;
    private int sensorOrientation;
    private PreviewGeometry previewGeometry;
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){} public void onDisplayRemoved(int id){}
        public void onDisplayChanged(int id){if(added && id==wm.getDefaultDisplay().getDisplayId())transform();}
    };
    private boolean realtimeTimestamp, destroyed, added,disableRotateCrop;
    private long brightnessAt,sampledAt,lastLatency,analysisEpoch;
    private static InferenceWorker<BitmapFrame,LocalDetector.Result> sharedWorker;
    private InferenceWorker<BitmapFrame,LocalDetector.Result> worker;
    private InferenceWorker.Ticket analysisTicket;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() { public void onReceive(Context c,Intent i) { fail("锁屏：已停止摄像头，请回应用重新开启"); }};
    private final Runnable watchdog = new Runnable() { public void run() {
        if(destroyed) return;
        long now=SystemClock.elapsedRealtime();
        if(state.phase==SessionState.Phase.STARTING || state.phase==SessionState.Phase.LIVE) {
            if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED || !Settings.canDrawOverlays(CameraWindowService.this)) fail("权限已撤回：不可用");
            else if(state.expired(now)) fail("画面或分析帧过期：不可用，请重新开启");
            else { state.promote(now); render(now); }
        }
        main.postDelayed(this,100);
    }};
    @Override public void onCreate() {
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        getSystemService(DisplayManager.class).registerDisplayListener(displayListener,main);
        synchronized(CameraWindowService.class){if(sharedWorker==null){Context app=getApplicationContext();sharedWorker=new InferenceWorker<>(SystemClock::elapsedRealtime,()->new LocalDetector(app));}worker=sharedWorker;}
        if(Build.VERSION.SDK_INT>=33) registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF));

    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(state.phase==SessionState.Phase.STARTING || state.phase==SessionState.Phase.LIVE) return START_NOT_STICKY;
        // 无自动恢复或重试。START_NOT_STICKY；仅 Activity 发出启动请求。
        try {
            NotificationManager nm=getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("camera",getString(R.string.notify_channel),NotificationManager.IMPORTANCE_LOW));
            if(Build.VERSION.SDK_INT>=30) startForeground(1,notification("正在启动"),ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
            else startForeground(1,notification("正在启动"));
            if(!Settings.canDrawOverlays(this) || checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { fail("权限不足：不可用"); stopSelf(); return START_NOT_STICKY; }
            if(root!=null) removeWindow();
            state.start(SystemClock.elapsedRealtime());
            createWindow();
            main.removeCallbacks(watchdog);main.post(watchdog);
        } catch(RuntimeException e) { publicReport="启动被系统拒绝：不可用，请检查权限并回到应用"; stopSelf(); }
        return START_NOT_STICKY;
    }
    private Notification notification(String status) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CameraWindowService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"camera").setSmallIcon(R.drawable.ic_stat_camera).setContentTitle(getString(R.string.app_name)+" · "+status)
            .setContentText(getString(R.string.notify_text)).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"停止",stop).build()).build();
    }
    private void createWindow() {
        root=(LinearLayout)LayoutInflater.from(this).inflate(R.layout.overlay_window,null);
        titleView=root.findViewById(R.id.overlay_title);
        View header=root.findViewById(R.id.overlay_header);
        drag(header,false);
        for(int i=0;i<((ViewGroup)header).getChildCount();i++) drag(((ViewGroup)header).getChildAt(i),false);
        texture=root.findViewById(R.id.overlay_preview);texture.setOpaque(false);
        cover=root.findViewById(R.id.overlay_cover);
        info=root.findViewById(R.id.overlay_info);
        recognition=root.findViewById(R.id.overlay_recognition);
        recognition.setOnCheckedChangeListener((b,checked)-> {
            policy.clear();analysisEpoch++;cancelAnalysis();
            if(checked && (!LocalDetector.AVAILABLE || state.phase!=SessionState.Phase.LIVE)) { b.setChecked(false);return; }
            state.recognition=checked;
            if(checked){long now=SystemClock.elapsedRealtime();state.analysisAt=now;analysisTicket=new InferenceWorker.Ticket(now);syncTicket();sampledAt=0;}
            if(!checked) { silence();if(info!=null)info.setText(R.string.info_recognition_off); }else if(info!=null)info.setText(R.string.info_recognition_on);
        });
        sound=root.findViewById(R.id.overlay_sound);
        Button stop=(Button)root.findViewById(R.id.overlay_stop);
        stop.setBackgroundTintList(null);
        stop.setOnClickListener(v->stopSelf());
        drag(root.findViewById(R.id.overlay_resize),true);
        layout=new WindowManager.LayoutParams(dp(300),dp(500),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.OPAQUE);
        layout.gravity=Gravity.TOP|Gravity.LEFT;layout.x=16;layout.y=80;clamp();
        wm.addView(root,layout);added=true;
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h) { openCamera(s); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h) { transform(); }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { if(!destroyed && state.phase!=SessionState.Phase.UNAVAILABLE) fail("显示 Surface 已销毁：不可用");return true; }
            public void onSurfaceTextureUpdated(SurfaceTexture s) {
                if(texture==null || destroyed || state.phase==SessionState.Phase.UNAVAILABLE)return;
                long now=SystemClock.elapsedRealtime();
                if(state.expired(now)){fail("采集或显示截止时间已过：不可用");return;}
                if(now-brightnessAt>=1000) { brightnessAt=now;Bitmap check=texture.getBitmap(16,12);
                    if(check!=null) {long light=0;for(int y=0;y<12;y++)for(int x=0;x<16;x++){int c=check.getPixel(x,y);light+=Color.red(c)+Color.green(c)+Color.blue(c);}check.recycle();
                        if(light<16*12*3*3){fail("画面过暗或隐私占位：不可用（仅白天）");return;}
                    }
                }
                state.display(now);state.promote(now);syncTicket();render(now);
                if(state.phase==SessionState.Phase.LIVE && state.recognition && now-sampledAt>=500 && !worker.busy())sample(now);
            }
        });
    }
    private void drag(View handle,boolean resize) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float x,y;int startX,startY,startW,startH;
            public boolean onTouch(View v,android.view.MotionEvent e) {
                if(e.getAction()==android.view.MotionEvent.ACTION_DOWN) { x=e.getRawX();y=e.getRawY();startX=layout.x;startY=layout.y;startW=layout.width;startH=layout.height;return true; }
                if(e.getAction()==android.view.MotionEvent.ACTION_MOVE) {
                    int dx=(int)(e.getRawX()-x),dy=(int)(e.getRawY()-y);
                    if(resize) { layout.width=Math.max(dp(260),startW+dx);layout.height=Math.max(dp(420),startH+dy); }
                    else { layout.x=startX+dx;layout.y=startY+dy; }
                    clamp();if(added) wm.updateViewLayout(root,layout);return true;
                }
                if(e.getAction()==android.view.MotionEvent.ACTION_UP) { v.performClick();return true; }return false;
            }
        });
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void clamp() {
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();wm.getDefaultDisplay().getMetrics(metrics);
        layout.width=Math.min(layout.width,metrics.widthPixels);layout.height=Math.min(layout.height,Math.max(1,metrics.heightPixels-dp(24)));
        layout.x=Math.max(0,Math.min(layout.x,metrics.widthPixels-layout.width));layout.y=Math.max(0,Math.min(layout.y,metrics.heightPixels-layout.height));
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config);if(added) {clamp();wm.updateViewLayout(root,layout);transform();} }
    private void transform() {
        if(texture==null || texture.getWidth()<=0 || texture.getHeight()<=0) return;
        PreviewGeometry next=new PreviewGeometry(BUFFER_WIDTH,BUFFER_HEIGHT,sensorOrientation,
            wm.getDefaultDisplay().getRotation()*90,texture.getWidth(),texture.getHeight());
        if(previewGeometry!=null && previewGeometry.viewWidth==next.viewWidth && previewGeometry.viewHeight==next.viewHeight && Arrays.equals(previewGeometry.matrix,next.matrix))return;
        previewGeometry=next;
        Matrix matrix=new Matrix();matrix.setValues(next.matrix);texture.setTransform(matrix);
        // 变换后旧结果不再与当前画面对应；等待新显示帧重新采样。
        policy.clear();silence();analysisEpoch++;cancelAnalysis();
        if(state.recognition){long now=SystemClock.elapsedRealtime();state.analysisAt=now;
            analysisTicket=new InferenceWorker.Ticket(now);syncTicket();sampledAt=now;}
    }
    static Bitmap samplePreview(TextureView view,PreviewGeometry geometry) {
        // getBitmap 复制的是 SurfaceTexture 内容，不包含 view 的 fit-center Matrix。
        // 读取系统已补偿 sensor 的天然尺寸，避免按浮窗尺寸拉伸；只补偿 display 一次。
        Bitmap natural=view.getBitmap(geometry.naturalWidth,geometry.naturalHeight);if(natural==null)return null;
        if(geometry.displayDegrees==0)return natural;
        Matrix rotation=new Matrix();rotation.setRotate(-geometry.displayDegrees);
        Bitmap sampled=Bitmap.createBitmap(natural,0,0,natural.getWidth(),natural.getHeight(),rotation,true);
        if(sampled!=natural)natural.recycle();return sampled;
    }
    @SuppressWarnings("MissingPermission") private void openCamera(SurfaceTexture surfaceTexture) {
        if(state.phase!=SessionState.Phase.STARTING) return;
        if(state.expired(SystemClock.elapsedRealtime())){fail("启动截止时间已过：不可用");return;}
        long token=state.generation;
        try {
            CameraManager manager=getSystemService(CameraManager.class);String selected=null;
            for(String id:manager.getCameraIdList()) {
                CameraCharacteristics c=manager.getCameraCharacteristics(id);
                if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(c.get(CameraCharacteristics.LENS_FACING))) {
                    StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if(map==null || !Arrays.asList(map.getOutputSizes(SurfaceTexture.class)).contains(new Size(640,480))) continue;
                    selected=id;sensorOrientation=c.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    disableRotateCrop=false;
                    if(Build.VERSION.SDK_INT>=31){int[] modes=c.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES);
                        if(modes!=null)for(int mode:modes)if(mode==CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)disableRotateCrop=true;}
                    realtimeTimestamp=Integer.valueOf(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME).equals(c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE));break;
                }
            }
            if(selected==null) { fail("没有支持 640×480 的后置摄像头：不可用");return; }
            surfaceTexture.setDefaultBufferSize(BUFFER_WIDTH,BUFFER_HEIGHT);transform();surface=new Surface(surfaceTexture);
            manager.openCamera(selected,new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice device) {
                    if(token!=state.generation || destroyed) { device.close();return; }
                    if(state.expired(SystemClock.elapsedRealtime())){device.close();fail("启动截止时间已过：不可用");return;}camera=device;
                    try { device.createCaptureSession(Collections.singletonList(surface),new CameraCaptureSession.StateCallback() {
                        public void onConfigured(CameraCaptureSession configured) {
                            if(token!=state.generation || destroyed) { configured.close();return; }
                            if(state.expired(SystemClock.elapsedRealtime())){configured.close();fail("启动截止时间已过：不可用");return;}session=configured;
                            try {
                                CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(surface);
                                if(Build.VERSION.SDK_INT>=31 && disableRotateCrop)request.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_NONE);
                                request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                session.setRepeatingRequest(request.build(),new CameraCaptureSession.CaptureCallback() {
                                    @Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result) {
                                        if(token!=state.generation || destroyed) return;
                                        Long stamp=result.get(CaptureResult.SENSOR_TIMESTAMP);if(stamp==null)return;
                                        long now=SystemClock.elapsedRealtime();
                                        if(state.expired(now)){fail("采集或显示截止时间已过：不可用");return;}
                                        if(realtimeTimestamp && (SystemClock.elapsedRealtimeNanos()-stamp>1200000000L || SystemClock.elapsedRealtimeNanos()-stamp<0)) { fail("采集时间戳过期：不可用");return; }
                                        state.capture(stamp,now);state.promote(now);syncTicket();
                                    }
                                },main);
                            } catch(CameraAccessException|RuntimeException e) { fail("无法开始采集：不可用"); }
                        }
                        public void onConfigureFailed(CameraCaptureSession s) { s.close();if(token==state.generation)fail("摄像头配置失败：不可用"); }
                    },main); } catch(CameraAccessException|RuntimeException e) { fail("建立采集会话失败：不可用"); }
                }
                public void onDisconnected(CameraDevice device) {device.close();if(token==state.generation)fail("摄像头断开或被其他应用占用：不可用");}
                public void onError(CameraDevice device,int error) {device.close();if(token==state.generation)fail("摄像头错误 / 被占用（"+error+"）：不可用");}
            },main);
        } catch(CameraAccessException|RuntimeException e) { fail("摄像头不可用：检查占用、权限、隐私开关"); }
    }
    private void syncTicket(){if(analysisTicket!=null){analysisTicket.captureAt=state.captureAt;analysisTicket.displayAt=state.displayAt;analysisTicket.analysisAt=state.analysisAt;}}
    private void cancelAnalysis(){if(analysisTicket!=null){worker.cancel(analysisTicket);analysisTicket=null;}}
    private void sample(long frameAt) {
        if(analysisTicket==null || !analysisTicket.allowed(SystemClock.elapsedRealtime()))return;
        if(previewGeometry==null)return;
        Bitmap bitmap=samplePreview(texture,previewGeometry);if(bitmap==null){fail("无法读取分析帧：不可用");return;}
        sampledAt=frameAt;long token=state.generation,epoch=analysisEpoch;InferenceWorker.Ticket ticket=analysisTicket;
        worker.submit(ticket,new BitmapFrame(bitmap),frameAt,(result,error)->main.post(()->{
            long now=SystemClock.elapsedRealtime();
            if(destroyed || ticket.cancelled || ticket!=analysisTicket || epoch!=analysisEpoch || token!=state.generation)return;
            if(state.expired(now)){fail("采集/显示/分析截止时间已过：不可用");return;}
            if(!state.acceptResult(token,frameAt,now))return;
            if(error!=null){fail("模型初始化或推理失败：不可用");return;}
            state.analysisAt=now;syncTicket();lastLatency=now-frameAt;
            info.setText(result.category.isEmpty()?"未识别到支持目标；不代表安全":"潜在目标："+result.category+"；请自行观察");
            if(policy.shouldAlert(result.category,now))alert();
        }));
    }
    private void alert(){
        if(!state.recognition || state.expired(SystemClock.elapsedRealtime()))return;
        Vibrator vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(vibrator!=null && vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createOneShot(150,VibrationEffect.DEFAULT_AMPLITUDE));
        if(sound.isChecked())try{activeTone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,40);ToneGenerator tone=activeTone;tone.startTone(ToneGenerator.TONE_PROP_BEEP,150);main.postDelayed(()->{if(activeTone==tone){tone.release();activeTone=null;}},250);}catch(RuntimeException ignored){silence();}
    }
    private void silence() {
        Vibrator vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(vibrator!=null)vibrator.cancel();
        if(activeTone!=null){activeTone.stopTone();activeTone.release();activeTone=null;}
    }
    private void render(long now) {
        if(cover==null)return;
        boolean live=state.phase==SessionState.Phase.LIVE && state.fresh(now);
        cover.setVisibility(live?View.GONE:View.VISIBLE);
        if(!live) cover.setText(state.reason);
        recognition.setEnabled(live && LocalDetector.AVAILABLE);
        publicReport=state.reason+"\n"+(state.recognition?"识别开着":"识别关着");
        if(live){cover.setText("");if(!state.recognition)info.setText(R.string.info_recognition_off);}
        if(titleView!=null) titleView.setText(live?R.string.window_live:R.string.window_idle);
    }
    private void fail(String message) {
        if(destroyed)return;
        cancelAnalysis();state.unavailable(message);policy.clear();silence();
        if(recognition!=null)recognition.setChecked(false);
        // 先不透明遮挡，再移除纹理，从视图树彻底清除最后一帧。
        if(cover!=null) {cover.setText(message);cover.setVisibility(View.VISIBLE);}
        closeCamera();
        if(texture!=null) {((FrameLayout)texture.getParent()).removeView(texture);texture=null;}
        if(info!=null)info.setText(R.string.info_stopped);
        if(root!=null)render(SystemClock.elapsedRealtime());
        publicReport=message+"；提醒已停止";
        getSystemService(NotificationManager.class).notify(1,notification("不可用；摄像头已释放"));
        stopForeground(STOP_FOREGROUND_DETACH);
    }
    private void closeCamera() {
        if(session!=null) {session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(surface!=null){surface.release();surface=null;}
    }

    private void removeWindow() {if(added){wm.removeViewImmediate(root);added=false;}root=null;texture=null;previewGeometry=null;}
    @Override public void onDestroy() {
        destroyed=true;cancelAnalysis();state.stop();policy.clear();silence();main.removeCallbacksAndMessages(null);closeCamera();
        removeWindow();unregisterReceiver(screenOff);getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager.class).cancel(1);publicReport="已停止；请主动开启";super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) {return null;}
}
