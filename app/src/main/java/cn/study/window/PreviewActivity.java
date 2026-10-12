package cn.study.window;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.*;
import android.util.Size;
import android.util.Rational;
import android.graphics.drawable.Icon;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.lang.ref.WeakReference;

/** Camera is owned by the visible preview Activity, including system PiP. */
public final class PreviewActivity extends Activity {
    private static WeakReference<PreviewActivity> active = new WeakReference<>(null);
    static void stopActivePreview() {
        PreviewActivity preview = active.get();
        if (preview != null && !preview.destroyed) preview.stopPreview();
    }
    private static volatile String publicReport = "已停止；请主动开启";
    public static String report() { return publicReport; }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SessionState state = new SessionState();
    private final AlertPolicy policy = new AlertPolicy();
    private ToneGenerator activeTone;
    private WindowManager wm;
    private LinearLayout root;
    private TextureView texture;
    private DetectionOverlay detectionOverlay;
    private long detectionAt;
    private TextView postureHint;
    private SensorManager sensors;
    private final PosturePolicy posturePolicy=new PosturePolicy();
    private final float[] gravity=new float[3];
    private boolean gravityReady;
    private long postureAt;
    private final SensorEventListener postureListener=new SensorEventListener() {
        public void onAccuracyChanged(Sensor sensor,int accuracy){}
        public void onSensorChanged(SensorEvent event) {
            if(state.phase!=SessionState.Phase.LIVE){posturePolicy.reset();return;}
            long now=SystemClock.elapsedRealtime();
            if(now-postureAt>1500){posturePolicy.reset();gravityReady=false;}
            postureAt=now;
            for(int i=0;i<3;i++)gravity[i]=event.sensor.getType()==Sensor.TYPE_GRAVITY || !gravityReady
                ? event.values[i] : 0.8f*gravity[i]+0.2f*event.values[i];
            gravityReady=true;
            boolean warn=posturePolicy.update(gravity[0],gravity[1],gravity[2],now);
            postureHint.setVisibility(warn?View.VISIBLE:View.GONE);
        }
    };
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
        public void onDisplayChanged(int id){if(root!=null && id==wm.getDefaultDisplay().getDisplayId())transform();}
    };
    private boolean realtimeTimestamp, destroyed, disableRotateCrop, resumed;
    private boolean initialStart;
    private Button pipButton, restartButton;
    private long brightnessAt,sampledAt,analysisEpoch;
    private static InferenceWorker<BitmapFrame,LocalDetector.Result> sharedWorker;
    private InferenceWorker<BitmapFrame,LocalDetector.Result> worker;
    private InferenceWorker.Ticket analysisTicket;
    private final BroadcastReceiver events = new BroadcastReceiver() {
        public void onReceive(Context c, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) fail("锁屏：摄像头已停止，请主动重新开启");
        }
    };
    private final Runnable watchdog = new Runnable() { public void run() {
        if(destroyed) return;
        long now=SystemClock.elapsedRealtime();
        if(detectionOverlay!=null && now-detectionAt>1000)detectionOverlay.clear();
        if(postureHint!=null && now-postureAt>1500){postureHint.setVisibility(View.GONE);posturePolicy.reset();}
        if(state.phase==SessionState.Phase.STARTING || state.phase==SessionState.Phase.LIVE) {
            if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) fail("权限已撤回：不可用");
            else if(state.expired(now)) fail("画面或分析帧过期：不可用，请重新开启");
            else { state.promote(now); render(now); }
        }
        main.postDelayed(this,100);
    }};
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        active = new WeakReference<>(this);
        wm = getWindowManager();
        sensors=getSystemService(SensorManager.class);
        getSystemService(DisplayManager.class).registerDisplayListener(displayListener, main);
        synchronized (PreviewActivity.class) {
            if (sharedWorker == null) {
                Context app = getApplicationContext();
                sharedWorker = new InferenceWorker<>(SystemClock::elapsedRealtime, () -> new LocalDetector(app));
            }
            worker = sharedWorker;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(events, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(events, filter);
        // Process recreation must not silently reopen the camera.
        initialStart = savedState == null;
        createWindow();
        main.post(watchdog);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (initialStart) { initialStart = false; startPreview(); }
        render(SystemClock.elapsedRealtime());
    }

    @Override protected void onPause() {
        resumed = false;
        // A PiP Activity is paused but still visible: keep its camera running.
        super.onPause();
    }

    @Override protected void onStop() {
        // Called when the PiP window is dismissed or the preview is no longer visible.
        // Do not gate this on isInPictureInPictureMode(): it can still be true on dismissal.
        fail("预览已离开屏幕，摄像头已停止；请主动重新开启");
        super.onStop();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (active.get() == this) active.clear();
        cancelAnalysis(); state.stop(); policy.clear(); silence();
        main.removeCallbacksAndMessages(null); stopPosture(); closeCamera();
        unregisterReceiver(events);
        getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        publicReport = "已停止；请主动开启";
        super.onDestroy();
    }

    private void startPreview() {
        if (!resumed || isInPictureInPictureMode()) return;
        KeyguardManager lock = getSystemService(KeyguardManager.class);
        if (lock.isKeyguardLocked() || !getSystemService(PowerManager.class).isInteractive()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail("请返回首页允许摄像头，再开启预览"); return;
        }
        if (state.phase == SessionState.Phase.STARTING || state.phase == SessionState.Phase.LIVE) return;
        if (texture != null) texture.setSurfaceTextureListener(null);
        state.start(SystemClock.elapsedRealtime());
        brightnessAt = sampledAt = 0;
        // Rebuild only after a stopped/failed session, never on a PiP size change.
        createWindow();
        startPosture();
        render(SystemClock.elapsedRealtime());
    }

    private void stopPreview() {
        fail("已停止；请主动开启");
        finish();
    }

    private PictureInPictureParams pipParams() {
        int rotation = wm.getDefaultDisplay().getRotation() * 90;
        boolean portrait = Math.floorMod(sensorOrientation - rotation, 180) != 0;
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
            .setAspectRatio(new Rational(portrait ? 3 : 4, portrait ? 4 : 3))
            .setActions(Collections.singletonList(stopAction()));
        if (texture != null) {
            Rect bounds = new Rect();
            if (texture.getGlobalVisibleRect(bounds)) builder.setSourceRectHint(bounds);
        }
        // Entry is explicit after the user has checked the viewfinder.
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(false);
        return builder.build();
    }

    private RemoteAction stopAction() {
        PendingIntent stop = PendingIntent.getBroadcast(this, 0,
            new Intent(this, StopPreviewReceiver.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new RemoteAction(Icon.createWithResource(this, R.drawable.ic_stop),
            getString(R.string.preview_stop), getString(R.string.preview_stop), stop);
    }

    private void enterPip() {
        if (!resumed || state.phase != SessionState.Phase.LIVE || !state.fresh(SystemClock.elapsedRealtime())) return;
        if (!PipSupport.available(this)) { showPipFallback(); return; }
        try {
            setCompactUi(true);
            if (!enterPictureInPictureMode(pipParams())) {
                setCompactUi(false); showPipFallback();
            }
        } catch (RuntimeException error) {
            setCompactUi(false); showPipFallback();
        }
    }

    private void showPipFallback() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.pip_unavailable_title).setMessage(R.string.pip_unavailable_body)
            .setNegativeButton(R.string.keep_preview, null);
        if (PipSupport.supported(this)) dialog.setPositiveButton(R.string.pip_settings,
            (d, which) -> PipSupport.openSettings(this));
        dialog.show();
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip, Configuration config) {
        super.onPictureInPictureModeChanged(inPip, config);
        setCompactUi(inPip);
    }

    private void setCompactUi(boolean compact) {
        if (root == null) return;
        int visibility = compact ? View.GONE : View.VISIBLE;
        root.findViewById(R.id.preview_header).setVisibility(visibility);
        root.findViewById(R.id.preview_controls).setVisibility(visibility);
        root.findViewById(R.id.preview_info).setVisibility(visibility);
        if (compact) root.setPadding(0, 0, 0, 0);
        root.requestApplyInsets();
        if (compact) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (texture != null) texture.post(this::transform);
    }

    private void createWindow() {
        previewGeometry = null;
        root=(LinearLayout)getLayoutInflater().inflate(R.layout.activity_preview,
            (ViewGroup)findViewById(android.R.id.content), false);
        titleView=root.findViewById(R.id.preview_title);
        texture=root.findViewById(R.id.camera_preview);texture.setOpaque(false);
        cover=root.findViewById(R.id.preview_cover);
        detectionOverlay=root.findViewById(R.id.detection_overlay);
        postureHint=root.findViewById(R.id.posture_hint);
        info=root.findViewById(R.id.preview_info);
        recognition=root.findViewById(R.id.preview_recognition);
        recognition.setOnCheckedChangeListener((b,checked)-> {
            policy.clear();detectionOverlay.clear();analysisEpoch++;cancelAnalysis();
            if(checked && (!LocalDetector.AVAILABLE || state.phase!=SessionState.Phase.LIVE)) { b.setChecked(false);return; }
            state.recognition=checked;
            if(checked){long now=SystemClock.elapsedRealtime();state.analysisAt=now;analysisTicket=new InferenceWorker.Ticket(now);syncTicket();sampledAt=0;}
            if(!checked) { if(sound!=null)sound.setChecked(false); silence();if(info!=null)info.setText(R.string.info_recognition_off); }else if(info!=null)info.setText(R.string.info_recognition_on);
        });
        sound=root.findViewById(R.id.preview_sound);
        Button stop=(Button)root.findViewById(R.id.preview_stop);
        stop.setBackgroundTintList(null);
        stop.setOnClickListener(v->stopPreview());
        pipButton=root.findViewById(R.id.enter_pip);
        pipButton.setBackgroundTintList(null);
        pipButton.setOnClickListener(v->enterPip());
        restartButton=root.findViewById(R.id.restart_preview);
        restartButton.setBackgroundTintList(null);
        restartButton.setOnClickListener(v->startPreview());
        sound.setOnCheckedChangeListener((button, checked) -> { if (!checked) silence(); });
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h) { openCamera(s); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h) { transform(); }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { if(!destroyed && (state.phase==SessionState.Phase.STARTING || state.phase==SessionState.Phase.LIVE)) fail("显示 Surface 已销毁：不可用");return true; }
            public void onSurfaceTextureUpdated(SurfaceTexture s) {
                if(texture==null || destroyed || (state.phase!=SessionState.Phase.STARTING && state.phase!=SessionState.Phase.LIVE))return;
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
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (!isInPictureInPictureMode()) view.setPadding(insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            else view.setPadding(0, 0, 0, 0);
            return insets;
        });
        root.requestApplyInsets();
        setCompactUi(isInPictureInPictureMode());
    }
    private void transform() {
        if(texture==null || texture.getWidth()<=0 || texture.getHeight()<=0) return;
        PreviewGeometry next=new PreviewGeometry(BUFFER_WIDTH,BUFFER_HEIGHT,sensorOrientation,
            wm.getDefaultDisplay().getRotation()*90,texture.getWidth(),texture.getHeight());
        if(previewGeometry!=null && previewGeometry.viewWidth==next.viewWidth && previewGeometry.viewHeight==next.viewHeight && Arrays.equals(previewGeometry.matrix,next.matrix))return;
        previewGeometry=next;
        Matrix matrix=new Matrix();matrix.setValues(next.matrix);texture.setTransform(matrix);
        // 变换后旧结果不再与当前画面对应；等待新显示帧重新采样。
        policy.clear();detectionOverlay.clear();silence();analysisEpoch++;cancelAnalysis();
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
            state.analysisAt=now;syncTicket();
            detectionAt=frameAt;detectionOverlay.show(result.detections,previewGeometry);
            AlertPolicy.Decision decision=policy.update(result.detections,now);
            info.setText(decision.message);
            if(decision.alert)alert();
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
        sound.setEnabled(live && state.recognition);
        pipButton.setEnabled(live);
        restartButton.setVisibility(state.phase==SessionState.Phase.UNAVAILABLE || state.phase==SessionState.Phase.STOPPED ? View.VISIBLE : View.GONE);
        publicReport=state.reason+"\n"+(state.recognition?"识别开着":"识别关着");
        if(live){cover.setText("");if(!state.recognition)info.setText(R.string.info_recognition_off);}
        if(titleView!=null) titleView.setText(live?R.string.window_live:R.string.window_idle);
    }
    private void fail(String message) {
        if(destroyed)return;
        cancelAnalysis();state.unavailable(message);policy.clear();silence();
        if(detectionOverlay!=null)detectionOverlay.clear();
        stopPosture();
        if(recognition!=null)recognition.setChecked(false);
        // 先不透明遮挡，再移除纹理，从视图树彻底清除最后一帧。
        if(cover!=null) {cover.setText(message);cover.setVisibility(View.VISIBLE);}
        closeCamera();
        if(texture!=null) {((FrameLayout)texture.getParent()).removeView(texture);texture=null;}
        if(info!=null)info.setText(R.string.info_stopped);
        if(root!=null)render(SystemClock.elapsedRealtime());
        publicReport=message+"；提醒已停止";
    }
    private void startPosture() {
        stopPosture();
        if(sensors==null)return;
        Sensor sensor=sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if(sensor==null)sensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(sensor!=null)sensors.registerListener(postureListener,sensor,SensorManager.SENSOR_DELAY_UI,main);
    }
    private void stopPosture() {
        if(sensors!=null)sensors.unregisterListener(postureListener);
        posturePolicy.reset();gravityReady=false;postureAt=0;
        if(postureHint!=null)postureHint.setVisibility(View.GONE);
    }
    private void closeCamera() {
        if(session!=null) {session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(surface!=null){surface.release();surface=null;}
    }

}
