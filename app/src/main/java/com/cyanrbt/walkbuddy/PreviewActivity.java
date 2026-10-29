package com.cyanrbt.walkbuddy;

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
import com.google.ar.core.ArCoreApk;
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
    private final DetectionFeedback feedback = new DetectionFeedback();
    private final AccessibilitySpeech speech = new AccessibilitySpeech();
    private boolean restoreDetection,restoreSound;
    private ToneGenerator activeTone;
    private WindowManager wm;
    private LinearLayout root;
    private TextureView texture;
    private DetectionOverlay detectionOverlay;
    private ArDepthCamera depthCamera;
    private DepthSnapshot depthSnapshot;
    private boolean useDepth,pendingDepthInstall,installDetection,installSound;
    private Button depthButton;
    private TextView depthStatus;
    private TextView depthGuideHint;
    private DepthGuide.Progress displayedDepthGuide;
    private boolean depthChecking = true;
    private ArCoreApk.Availability depthAvailability;
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
    private boolean previewSeen;
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
        if(detectionOverlay!=null && state.recognition)showFeedback(now);
        if(useDepth)renderDepthStatus(now);
        if(useDepth)renderDepthGuide();
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
        previewSeen = getSharedPreferences(HomeJourney.PREFERENCES, MODE_PRIVATE)
            .getBoolean(HomeJourney.PREVIEW_SEEN, false);
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
    }

    @Override protected void onStart() {
        super.onStart();
        main.removeCallbacks(watchdog);
        main.post(watchdog);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (initialStart) { initialStart = false; startPreview(); }
        if(pendingDepthInstall){pendingDepthInstall=false;enableDepth(false);}
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
        main.removeCallbacks(watchdog);
        fail("预览已离开屏幕，摄像头已停止；请主动重新开启");
        super.onStop();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (active.get() == this) active.clear();
        cancelAnalysis(); state.stop(); feedback.clear(); speech.clear(); silence();
        main.removeCallbacksAndMessages(null); stopPosture(); closeCamera();
        unregisterReceiver(events);
        getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        publicReport = "已停止；请主动开启";
        super.onDestroy();
    }

    private void startPreview() {
        if (destroyed || !resumed || isInPictureInPictureMode()) return;
        KeyguardManager lock = getSystemService(KeyguardManager.class);
        if (lock.isKeyguardLocked() || !getSystemService(PowerManager.class).isInteractive()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail("请返回首页允许摄像头，再开启预览"); return;
        }
        if (state.phase == SessionState.Phase.STARTING || state.phase == SessionState.Phase.LIVE) return;
        if (texture != null) texture.setSurfaceTextureListener(null);
        state.start(SystemClock.elapsedRealtime());
        brightnessAt = sampledAt = 0;depthSnapshot=null;
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
        if (inPip) getSharedPreferences(HomeJourney.PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(HomeJourney.PIP_SEEN, true).apply();
        setCompactUi(inPip);
    }

    private void setCompactUi(boolean compact) {
        if (root == null) return;
        int visibility = compact ? View.GONE : View.VISIBLE;
        root.findViewById(R.id.preview_header).setVisibility(visibility);
        root.findViewById(R.id.preview_controls).setVisibility(visibility);
        root.findViewById(R.id.preview_info).setVisibility(visibility);
        root.findViewById(R.id.depth_status).setVisibility(useDepth?View.VISIBLE:visibility);
        root.findViewById(R.id.depth_guide).setVisibility(compact && !useDepth ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.risk_legend).setVisibility(visibility);
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
        displayedDepthGuide = null;
        root=(LinearLayout)getLayoutInflater().inflate(R.layout.activity_preview,
            (ViewGroup)findViewById(android.R.id.content), false);
        titleView=root.findViewById(R.id.preview_title);
        texture=root.findViewById(R.id.camera_preview);texture.setOpaque(false);
        cover=root.findViewById(R.id.preview_cover);
        detectionOverlay=root.findViewById(R.id.detection_overlay);
        postureHint=root.findViewById(R.id.posture_hint);
        info=root.findViewById(R.id.preview_info);
        depthStatus=root.findViewById(R.id.depth_status);
        depthGuideHint=root.findViewById(R.id.depth_guide_hint);
        depthButton=root.findViewById(R.id.enable_depth);
        depthButton.setOnClickListener(v->chooseDepth());
        refreshDepthAvailability();
        renderDepthGuide();
        recognition=root.findViewById(R.id.preview_recognition);
        recognition.setOnCheckedChangeListener((b,checked)-> {
            feedback.clear();detectionOverlay.clear();analysisEpoch++;cancelAnalysis();
            if(checked && (!LocalDetector.AVAILABLE || state.phase!=SessionState.Phase.LIVE)) { b.setChecked(false);return; }
            state.recognition=checked;
            b.setText(checked?"检测：已开启":"检测：已关闭");
            if(checked){long now=SystemClock.elapsedRealtime();state.analysisAt=now;analysisTicket=new InferenceWorker.Ticket(now);syncTicket();sampledAt=0;}
            if(!checked) { if(sound!=null)sound.setChecked(false); silence();if(info!=null)info.setText(R.string.info_recognition_off); }else if(info!=null)info.setText(R.string.info_recognition_on);
            renderDepthGuide();
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
        sound.setOnCheckedChangeListener((button, checked) -> { button.setText(checked?"声音：已开启":"声音：已关闭"); if (!checked) silence(); });
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
                if(state.phase==SessionState.Phase.LIVE && state.recognition) {
                    if(useDepth)processDepth(now);
                    if(now-sampledAt>=500 && !worker.busy())sample(now);
                }
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
        feedback.clear();detectionOverlay.clear();silence();analysisEpoch++;cancelAnalysis();
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
        if(useDepth){
            sensorOrientation=90;transform();
            depthCamera=new ArDepthCamera(this,surfaceTexture,new ArDepthCamera.Callback(){
                public void frame(long timestamp,long capturedAt,DepthSnapshot depth){
                    if(destroyed || token!=state.generation)return;
                    long now=SystemClock.elapsedRealtime();
                    if(now-capturedAt>500 || state.expired(now)){fail("增强画面已过期，请重新开启");return;}
                    state.capture(timestamp,capturedAt);syncTicket();depthSnapshot=depth;
                    renderDepthStatus(now);
                    renderDepthGuide();
                }
                public void failed(String reason){if(!destroyed && token==state.generation){useDepth=false;fail(reason);refreshDepthAvailability();}}
            });depthCamera.start();return;
        }
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
            boolean notify=feedback.basic(result.detections,frameAt,now);
            showFeedback(now);
            if(notify)alert();
        }));
    }
    private void alert(){
        if(!state.recognition || state.expired(SystemClock.elapsedRealtime()))return;
        pendingSpeak=true;
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
        if (live && !previewSeen) {
            previewSeen = true;
            getSharedPreferences(HomeJourney.PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(HomeJourney.PREVIEW_SEEN, true).apply();
        }
        cover.setVisibility(live?View.GONE:View.VISIBLE);
        if(!live) setTextIfChanged(cover,state.reason);
        recognition.setEnabled(live && LocalDetector.AVAILABLE);
        sound.setEnabled(live && state.recognition);
        if(live && restoreDetection){boolean enableSound=restoreSound;restoreDetection=restoreSound=false;recognition.setChecked(true);sound.setChecked(enableSound);}
        pipButton.setEnabled(live);
        restartButton.setVisibility(state.phase==SessionState.Phase.UNAVAILABLE || state.phase==SessionState.Phase.STOPPED ? View.VISIBLE : View.GONE);
        publicReport=state.reason+"\n"+(state.recognition?"识别开着":"识别关着");
        if(live){setTextIfChanged(cover,"");if(!state.recognition)setTextIfChanged(info,getText(R.string.info_recognition_off));}
        if(titleView!=null) setTextIfChanged(titleView,getText(live?R.string.window_live:R.string.window_idle));
        renderDepthGuide();
    }
    private void fail(String message) {
        if(destroyed)return;
        restoreDetection=restoreSound=false;
        cancelAnalysis();state.unavailable(message);feedback.clear();speech.clear();pendingSpeak=false;silence();
        if(detectionOverlay!=null)detectionOverlay.clear();
        stopPosture();
        if(recognition!=null)recognition.setChecked(false);
        // 先不透明遮挡，再移除纹理，从视图树彻底清除最后一帧。
        if(cover!=null) {cover.setText(message);cover.setVisibility(View.VISIBLE);}
        closeCamera();
        if(texture!=null) {((FrameLayout)texture.getParent()).removeView(texture);texture=null;}
        if(info!=null)info.setText(R.string.info_stopped);
        if(root!=null){render(SystemClock.elapsedRealtime());renderDepthGuide();}
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
    private void refreshDepthAvailability() {
        if(depthStatus==null)return;
        if(useDepth){depthChecking=false;depthStatus.setText("深度增强 · 正在建立环境");renderDepthGuide();return;}
        depthChecking=true;depthStatus.setText("基础识别 · 尚未启用测距");renderDepthGuide();
        try{ArCoreApk.getInstance().checkAvailabilityAsync(this,availability->main.post(()->{
            if(destroyed || useDepth)return;depthAvailability=availability;depthChecking=false;
            depthStatus.setText(availability.isSupported()?"可尝试增强 · 启用后按分步提示操作":"基础识别 · 增强服务暂不可用");
            renderDepthGuide();
        }));}catch(RuntimeException|LinkageError error){depthChecking=false;depthAvailability=ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE;depthStatus.setText("基础识别 · 增强服务暂不可用");renderDepthGuide();}
    }
    private void chooseDepth() {
        if(!resumed || isInPictureInPictureMode())return;
        if(useDepth){switchDepth(false);return;}
        if(depthAvailability==ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE){
            new AlertDialog.Builder(this).setTitle("此设备暂不支持增强测距").setMessage("基础模式仍可捕获模型认识的物体，但无法提供深度轮廓或距离分级。").setPositiveButton("知道了",null).show();return;
        }
        new AlertDialog.Builder(this).setTitle(R.string.depth_intro_title)
            .setMessage(getString(R.string.depth_intro_body)+"\n\n"+getString(R.string.depth_intro_privacy))
            .setNegativeButton("继续基础模式",null)
            .setNeutralButton("服务隐私说明",(dialog,which)->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://policies.google.com/privacy")));}catch(RuntimeException ignored){}})
            .setPositiveButton("继续启用",(dialog,which)->enableDepth(true)).show();
    }
    private void enableDepth(boolean userRequested) {
        if(!resumed || destroyed)return;
        if(userRequested){installDetection=state.recognition;installSound=sound.isChecked();}
        try{
            if(ArCoreApk.getInstance().requestInstall(this,userRequested)==ArCoreApk.InstallStatus.INSTALL_REQUESTED){pendingDepthInstall=true;fail("等待增强服务安装；返回后继续");return;}
            switchDepth(true);restoreDetection=installDetection;restoreSound=installSound;
        }catch(Exception|LinkageError error){pendingDepthInstall=false;useDepth=false;refreshDepthAvailability();info.setText("增强服务暂不可用，请继续基础模式");}
    }
    private void switchDepth(boolean enabled) {
        boolean detection=state.recognition,audio=sound.isChecked();
        fail("正在切换观察模式");useDepth=enabled;restoreDetection=detection;restoreSound=audio;
        long token=state.generation;
        ArDepthCamera.afterRelease(()->main.postDelayed(()->{if(token==state.generation)startPreview();},250));
    }
    private void processDepth(long now) {
        boolean notify=feedback.depth(depthSnapshot,-wm.getDefaultDisplay().getRotation()*90,now);
        showFeedback(now);
        if(notify)alert();
    }
    private boolean pendingSpeak;
    private void renderDepthStatus(long now) {
        if(depthSnapshot==null || depthStatus==null)return;
        setTextIfChanged(depthStatus,depthSnapshot.fresh(now)
            ?depthSnapshot.status+" · 基础识别"+(state.recognition?"继续":"未开启")
            :"深度暂不可用 · 未测到不代表可通行");
    }
    private void showFeedback(long now) {
        AlertPolicy.Decision visible=feedback.visible(now);
        detectionOverlay.show(visible.detections,previewGeometry);setTextIfChanged(info,visible.message);
        Detection.Risk worst=Detection.Risk.CAPTURED;
        for(Detection d:visible.detections)if(d.risk.ordinal()>worst.ordinal())worst=d.risk;
        boolean alert=pendingSpeak;pendingSpeak=false;
        String spoken=speech.update(visible.message,worst,alert,now);
        if(spoken!=null && info!=null)info.announceForAccessibility(spoken);
    }
    private void renderDepthGuide() {
        if(root==null || depthGuideHint==null)return;
        Boolean supported=null;
        if(!depthChecking && depthAvailability!=null)supported=depthAvailability.isSupported();
        DepthSnapshot.Stage stage=null;
        boolean groundReady=false,depthReady=false;
        if(useDepth && depthSnapshot!=null){
            stage=depthSnapshot.stage;groundReady=depthSnapshot.groundReady;depthReady=depthSnapshot.depthReady;
            long now=SystemClock.elapsedRealtime();
            if(!depthSnapshot.fresh(now)){stage=DepthSnapshot.Stage.DEPTH_STALE;depthReady=false;}
        }
        DepthGuide.Progress progress=DepthGuide.resolve(useDepth,supported,depthChecking,stage,groundReady,depthReady,state.recognition);
        if(progress.samePresentation(displayedDepthGuide))return;
        displayedDepthGuide=progress;
        paintStep(R.id.guide_step_service,R.id.guide_dot_service,R.id.guide_label_service,progress.serviceDone,progress.active==DepthGuide.Step.SERVICE);
        paintStep(R.id.guide_step_ground,R.id.guide_dot_ground,R.id.guide_label_ground,progress.groundDone,progress.active==DepthGuide.Step.GROUND);
        paintStep(R.id.guide_step_depth,R.id.guide_dot_depth,R.id.guide_label_depth,progress.depthDone,progress.active==DepthGuide.Step.DEPTH);
        paintStep(R.id.guide_step_alert,R.id.guide_dot_alert,R.id.guide_label_alert,progress.alertDone,progress.active==DepthGuide.Step.ALERT);
        setTextIfChanged(depthGuideHint,progress.hint);
        if(depthButton!=null)setTextIfChanged(depthButton,progress.buttonLabel);
    }
    private static void setTextIfChanged(TextView view,CharSequence text) {
        if(!android.text.TextUtils.equals(view.getText(),text))view.setText(text);
    }
    private void paintStep(int stepId,int dotId,int labelId,boolean done,boolean active) {
        View step=root.findViewById(stepId);
        TextView dot=root.findViewById(dotId),label=root.findViewById(labelId);
        String name=label.getText().toString();
        if(done){
            dot.setBackgroundResource(R.drawable.bg_step_done);
            dot.setTextColor(getColor(R.color.ink));
            label.setTextColor(getColor(R.color.mint_soft));
            step.setContentDescription(name+"，已完成");
        }else if(active){
            dot.setBackgroundResource(R.drawable.bg_step_active);
            dot.setTextColor(getColor(R.color.ink));
            label.setTextColor(getColor(R.color.warning));
            step.setContentDescription(name+"，进行中");
        }else{
            dot.setBackgroundResource(R.drawable.bg_step_todo);
            dot.setTextColor(getColor(R.color.on_ink_dim));
            label.setTextColor(getColor(R.color.on_ink_dim));
            step.setContentDescription(name+"，未开始");
        }
    }
    private void closeCamera() {
        if(depthCamera!=null){depthCamera.stop();depthCamera=null;}depthSnapshot=null;
        if(session!=null) {session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(surface!=null){surface.release();surface=null;}
    }

}
