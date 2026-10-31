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
    static boolean hasPreview() {
        PreviewActivity preview=active.get();
        return preview!=null && !preview.destroyed && !preview.isFinishing();
    }
    static SessionState.Phase homePhase() {
        PreviewActivity preview=active.get();
        return preview==null || preview.destroyed || preview.isFinishing()?SessionState.Phase.STOPPED:preview.state.phase;
    }
    static void stopActivePreview() {
        PreviewActivity preview = active.get();
        if (preview != null && !preview.destroyed) preview.stopPreview();
    }
    static void stopPipDetection(String owner,long generation) {
        PreviewActivity preview=active.get();
        if(preview==null || preview.destroyed || preview.isFinishing()
            || !preview.pipOwner.equals(owner) || preview.state.generation!=generation
            || !preview.isInPictureInPictureMode() || !preview.state.recognition)return;
        preview.pauseDetection(R.string.info_detection_paused);
    }
    static void setPipSound(String owner,long generation,long revision,long visit,boolean enabled) {
        PreviewActivity preview=active.get();
        if(preview==null || preview.destroyed || preview.isFinishing() || !preview.pipOwner.equals(owner)
            || preview.state.generation!=generation || preview.soundRevision!=revision || preview.pipSoundVisit!=visit
            || !preview.isInPictureInPictureMode() || preview.state.phase!=SessionState.Phase.LIVE || !preview.state.recognition
            || preview.sound==null || !preview.sound.isEnabled() || preview.sound.isChecked()==enabled
            || preview.state.expired(SystemClock.elapsedRealtime()))return;
        preview.sound.setChecked(enabled);
    }
    static final HomeState homeState=new HomeState();
    private final Object homeOwner=new Object();
    private final java.util.function.Supplier<String> homeNotice=this::detectionNoticeText;
    public static String report() { return homeState.report(); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SessionState state = new SessionState();
    private long soundRevision,pipSoundVisit,submittedSoundRevision=-1,submittedSoundVisit=-1;
    private final DetectionFeedback feedback = new DetectionFeedback();
    private final SessionDiagnostics diagnostics=new SessionDiagnostics();
    private static final String PROBLEM_INFO_STATE="walkbuddy.problem_info.v1";
    static final String REMINDERS_PREFERENCES="walkbuddy_reminders",VIBRATION_PREFERENCE="vibration_enabled";
    private RiskAnnouncer announcer;
    private boolean restoreDetection,restoreSound;
    private TonePlayer.Session activeTone;
    private static TonePlayer sharedToneWorker;
    private TonePlayer toneWorker;
    private RiskHaptics haptics;
    private WindowManager wm;
    private LinearLayout root;
    private TextureView texture;
    private DetectionOverlay detectionOverlay;
    private ArDepthCamera depthCamera;
    private DepthSnapshot depthSnapshot;
    private boolean useDepth,pendingDepthInstall,installDetection,installSound,compactUi;
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
            boolean previewOnly=PreviewStatus.showNotice(state.fresh(now),state.recognition,detectionNotice!=0,compactUi);
            postureHint.setVisibility(warn && !previewOnly?View.VISIBLE:View.GONE);
        }
    };
    private TextView cover, info, titleView;
    private CheckBox recognition, sound, vibration;
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
    private final String pipOwner=UUID.randomUUID().toString();
    private int pipActionState=-1;
    private PictureInPictureParams submittedPipParams;
    private Rect submittedPipBounds;
    private int submittedPipRotation=-1;
    private ViewTreeObserver.OnGlobalLayoutListener pipBoundsListener;
    private boolean initialStart;
    private RecoveryGuide.Reason recoveryReason=RecoveryGuide.Reason.HIDDEN;
    private boolean previewSeen;
    private Button pipButton, restartButton;
    private long sampledAt,analysisEpoch;
    private long lastReadbackQueueMs=-1,lastSampleCopyMs=-1,lastResultDeliveryMs=-1;
    private BrightnessReadback brightnessReadback;
    private final ReadbackTiming readbackTiming=new ReadbackTiming();
    private Runnable pendingCapture;
    private Runnable pendingReadback;
    private boolean readingTexture;
    private long deferredCaptureCount;
    private long staleStartupGeneration=-1;
    private static InferenceWorker<BitmapFrame,LocalDetector.Result> sharedWorker;
    private InferenceWorker<BitmapFrame,LocalDetector.Result> worker;
    private InferenceWorker.Ticket analysisTicket;
    private boolean analysisReady,analysisHasResult;
    private int detectionNotice;
    private final BroadcastReceiver events = new BroadcastReceiver() {
        public void onReceive(Context c, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())
                && (!getSystemService(PowerManager.class).isInteractive() || getSystemService(KeyguardManager.class).isKeyguardLocked()))
                fail(RecoveryGuide.Reason.LOCKED,"锁屏：摄像头已停止，请主动重新开启");
        }
    };
    private final Runnable watchdog = new Runnable() { public void run() {
        if(!needsWatchdog()) return;
        long now=SystemClock.elapsedRealtime();
        if(!checkHealth(now))return;
        if(detectionOverlay!=null && state.recognition)showFeedback(now);
        if(useDepth)renderDepthStatus(now);
        if(useDepth)renderDepthGuide();
        if(postureHint!=null && now-postureAt>1500){postureHint.setVisibility(View.GONE);posturePolicy.reset();}
        if(state.phase==SessionState.Phase.STARTING || state.phase==SessionState.Phase.LIVE) {
            if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) fail(RecoveryGuide.Reason.PERMISSION,"权限已撤回：不可用");
            else { state.promote(now); render(now); }
        }
        // fail() can run from this callback: never re-arm a stopped session.
        if(needsWatchdog())main.postDelayed(this,100);
    }};
    private boolean needsWatchdog() {
        // PiP is paused but visible, so resumed is deliberately not a condition.
        return !destroyed && (state.phase==SessionState.Phase.STARTING || state.phase==SessionState.Phase.LIVE);
    }
    private void scheduleWatchdog() {
        main.removeCallbacks(watchdog);
        if(needsWatchdog())main.postDelayed(watchdog,100);
    }
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        haptics=new RiskHaptics(new AndroidHaptics((Vibrator)getSystemService(VIBRATOR_SERVICE)));
        if(savedState!=null)diagnostics.restore(savedState.getString(PROBLEM_INFO_STATE));
        previewSeen = getSharedPreferences(HomeJourney.PREFERENCES, MODE_PRIVATE)
            .getBoolean(HomeJourney.PREVIEW_SEEN, false);
        active = new WeakReference<>(this);
        homeState.attach(homeOwner);
        wm = getWindowManager();
        sensors=getSystemService(SensorManager.class);
        getSystemService(DisplayManager.class).registerDisplayListener(displayListener, main);
        synchronized (PreviewActivity.class) {
            if (sharedWorker == null) {
                Context app = getApplicationContext();
                // Visible-camera inference has a deadline. Stay below main without joining
                // the less-urgent background scheduling group before native workers are created.
                sharedWorker = new InferenceWorker<>(SystemClock::elapsedRealtime,
                    () -> createWorkerDetector(app,android.os.Process.THREAD_PRIORITY_DEFAULT
                        +android.os.Process.THREAD_PRIORITY_LESS_FAVORABLE));
            }
            worker = sharedWorker;
            if(sharedToneWorker==null)sharedToneWorker=new TonePlayer(SystemClock::elapsedRealtime,AndroidTone::new);
            toneWorker=sharedToneWorker;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(events, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(events, filter);
        // Process recreation must not silently reopen the camera.
        initialStart = savedState == null;
        createWindow();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        String report=problemInfoText();
        if(report.isEmpty())out.remove(PROBLEM_INFO_STATE);else out.putString(PROBLEM_INFO_STATE,report);
        super.onSaveInstanceState(out);
    }

    @Override protected void onStart() {
        super.onStart();
        scheduleWatchdog();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        setCompactUi(isInPictureInPictureMode());
        if (initialStart) { initialStart = false; startPreview(); }
        if(pendingDepthInstall){pendingDepthInstall=false;enableDepth(false);}
        render(SystemClock.elapsedRealtime());
        tracePip("resume");
    }

    @Override protected void onPause() {
        resumed = false;
        syncKeepScreenOn();
        tracePip("pause");
        // A PiP Activity is paused but still visible: keep its camera running.
        super.onPause();
    }

    @Override protected void onStop() {
        // Called when the PiP window is dismissed or the preview is no longer visible.
        // Do not gate this on isInPictureInPictureMode(): it can still be true on dismissal.
        main.removeCallbacks(watchdog);
        fail(RecoveryGuide.Reason.HIDDEN,"预览已离开屏幕，摄像头已停止；请主动重新开启");
        super.onStop();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        syncKeepScreenOn();
        if (active.get() == this) active.clear();
        cancelAnalysis(); state.stop(); feedback.clear(); if(announcer!=null)announcer.clear(); silence();
        main.removeCallbacksAndMessages(null); stopPosture(); closeCamera();
        unregisterReceiver(events);
        getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        homeState.detach(homeOwner);
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        syncKeepScreenOn();
    }

    private void syncKeepScreenOn() {
        boolean keep=!destroyed && resumed && hasWindowFocus() && !compactUi && !isInPictureInPictureMode()
            && (state.phase==SessionState.Phase.STARTING
                || state.phase==SessionState.Phase.LIVE && state.fresh(SystemClock.elapsedRealtime()));
        int flag=WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        // Update only this flag, and only on a transition; frame callbacks must not repeat window IPC.
        if(((getWindow().getAttributes().flags&flag)!=0)!=keep)getWindow().setFlags(keep?flag:0,flag);
    }

    private void startPreview() {
        tracePip("start-request");
        if (destroyed || !resumed || isInPictureInPictureMode()) return;
        KeyguardManager lock = getSystemService(KeyguardManager.class);
        if (lock.isKeyguardLocked() || !getSystemService(PowerManager.class).isInteractive()) return;
        if(state.phase!=SessionState.Phase.STARTING && state.phase!=SessionState.Phase.LIVE)diagnostics.clear();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail(RecoveryGuide.Reason.PERMISSION,"摄像头未获允许，预览已停止"); return;
        }
        if (state.phase == SessionState.Phase.STARTING || state.phase == SessionState.Phase.LIVE) return;
        if (texture != null) texture.setSurfaceTextureListener(null);
        state.start(SystemClock.elapsedRealtime());
        scheduleWatchdog();
        lastReadbackQueueMs=lastSampleCopyMs=lastResultDeliveryMs=-1;
        readbackTiming.reset(state.generation);
        sampledAt = 0;depthSnapshot=null;
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
        return pipParams(pipSourceBounds());
    }

    private Rect pipSourceBounds() {
        if(texture==null || previewGeometry==null || previewGeometry.viewWidth!=texture.getWidth()
            || previewGeometry.viewHeight!=texture.getHeight())return null;
        Rect visible=new Rect();Point origin=new Point();
        if(!texture.getGlobalVisibleRect(visible,origin))return null;
        int[] bounds=previewGeometry.visibleBounds(origin.x,origin.y,visible.left,visible.top,visible.right,visible.bottom);
        return bounds==null?null:new Rect(bounds[0],bounds[1],bounds[2],bounds[3]);
    }

    private PictureInPictureParams pipParams(Rect sourceBounds) {
        int rotation = wm.getDefaultDisplay().getRotation() * 90;
        boolean portrait = Math.floorMod(sensorOrientation - rotation, 180) != 0;
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
            .setAspectRatio(new Rational(portrait ? 3 : 4, portrait ? 4 : 3))
            .setActions(pipActions());
        if(sourceBounds!=null)builder.setSourceRectHint(sourceBounds);
        // Entry is explicit after the user has checked the viewfinder.
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(false);
        return builder.build();
    }

    private void syncPipSourceBounds() {
        if(destroyed || texture==null || !texture.isAttachedToWindow()
            || state.phase!=SessionState.Phase.STARTING && state.phase!=SessionState.Phase.LIVE
            || !PipSupport.supported(this))return;
        Rect bounds=pipSourceBounds();
        if(bounds==null)return;
        int rotation=wm.getDefaultDisplay().getRotation();
        if(bounds.equals(submittedPipBounds) && rotation==submittedPipRotation)return;
        try {
            PictureInPictureParams params=pipParams(bounds);
            setPictureInPictureParams(params);submittedPipParams=params;
            submittedPipBounds=new Rect(bounds);submittedPipRotation=rotation;
        } catch(RuntimeException error){android.util.Log.w("WalkBuddyPip","Unable to update animation bounds",error);}
    }

    private void stopTrackingPipBounds() {
        if(root!=null && pipBoundsListener!=null && root.getViewTreeObserver().isAlive())
            root.getViewTreeObserver().removeOnGlobalLayoutListener(pipBoundsListener);
        pipBoundsListener=null;
    }

    private RemoteAction stopAction() {
        PendingIntent stop = PendingIntent.getBroadcast(this, 0,
            new Intent(this, StopPreviewReceiver.class).setAction(StopPreviewReceiver.STOP_PREVIEW),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new RemoteAction(Icon.createWithResource(this, R.drawable.ic_stop),
            getString(R.string.preview_stop), getString(R.string.preview_stop), stop);
    }

    private List<RemoteAction> pipActions() {
        List<RemoteAction> actions=new ArrayList<>();
        for(PipMenu.Item item:PipMenu.items(getMaxNumPictureInPictureActions(),state.phase,state.recognition,sound!=null && sound.isChecked())) {
            if(item==PipMenu.Item.STOP_PREVIEW){actions.add(stopAction());continue;}
            if(item==PipMenu.Item.ENABLE_SOUND || item==PipMenu.Item.DISABLE_SOUND) {
                boolean enabled=item==PipMenu.Item.ENABLE_SOUND;
                Intent intent=new Intent(this,StopPreviewReceiver.class).setAction(StopPreviewReceiver.SET_SOUND)
                    .setData(android.net.Uri.parse("walkbuddy://sound/"+pipOwner+"/"+state.generation+"/"+soundRevision+"/"+pipSoundVisit+"/"+enabled))
                    .putExtra(StopPreviewReceiver.OWNER,pipOwner).putExtra(StopPreviewReceiver.GENERATION,state.generation)
                    .putExtra(StopPreviewReceiver.SOUND_REVISION,soundRevision).putExtra(StopPreviewReceiver.PIP_VISIT,pipSoundVisit)
                    .putExtra(StopPreviewReceiver.SOUND_ENABLED,enabled);
                PendingIntent soundAction=PendingIntent.getBroadcast(this,2,intent,PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                actions.add(new RemoteAction(Icon.createWithResource(this,enabled?R.drawable.ic_sound_on:R.drawable.ic_sound_off),
                    getString(enabled?R.string.pip_sound_on:R.string.pip_sound_off),
                    getString(enabled?R.string.pip_sound_on_description:R.string.pip_sound_off_description),soundAction));
                continue;
            }
            Intent intent=new Intent(this,StopPreviewReceiver.class).setAction(StopPreviewReceiver.STOP_DETECTION)
                .setData(android.net.Uri.parse("walkbuddy://detection/"+pipOwner+"/"+state.generation))
                .putExtra(StopPreviewReceiver.OWNER,pipOwner).putExtra(StopPreviewReceiver.GENERATION,state.generation);
            PendingIntent stop=PendingIntent.getBroadcast(this,1,intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            actions.add(new RemoteAction(Icon.createWithResource(this,R.drawable.ic_detection_off),
                getString(R.string.pip_stop_detection),getString(R.string.pip_stop_detection_description),stop));
        }
        return actions;
    }

    private void syncPipActions() {
        if(!isInPictureInPictureMode())return;
        int next=state.phase==SessionState.Phase.LIVE && state.recognition?1:0;
        if(pipActionState==next && submittedSoundRevision==soundRevision && submittedSoundVisit==pipSoundVisit)return;
        try {
            PictureInPictureParams params=pipParams();
            setPictureInPictureParams(params);
            submittedPipParams=params;pipActionState=next;
            submittedSoundRevision=soundRevision;submittedSoundVisit=pipSoundVisit;
        }
        catch(RuntimeException error){android.util.Log.w("WalkBuddyPip","Unable to update menu actions",error);}
    }

    private void enterPip() {
        tracePip("request");
        if (!resumed || state.phase != SessionState.Phase.LIVE || !state.fresh(SystemClock.elapsedRealtime())) return;
        if (!PipSupport.available(this)) { showPipFallback(); return; }
        try {
            ++pipSoundVisit;
            PictureInPictureParams params=pipParams();
            setCompactUi(true);
            boolean entered=enterPictureInPictureMode(params);
            if(entered)submittedPipParams=params;
            tracePip("accepted="+entered);
            if (!entered) {
                setCompactUi(false); showPipFallback();
            }
        } catch (RuntimeException error) {
            tracePip("rejected="+error.getClass().getSimpleName());
            setCompactUi(false); showPipFallback();
        }
    }

    private void tracePip(String event) {
        long now=SystemClock.elapsedRealtime();
        android.util.Log.i("WalkBuddyPip",event+" resumed="+resumed+" phase="+state.phase
            +" pip="+isInPictureInPictureMode()+" focus="+hasWindowFocus()
            +" captureGapMs="+(state.captureAt>0?now-state.captureAt:-1)
            +" displayGapMs="+(state.displayAt>0?now-state.displayAt:-1));
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
        if(!inPip)++pipSoundVisit;
        if (inPip && !getSharedPreferences(HomeJourney.PREFERENCES, MODE_PRIVATE).getBoolean(HomeJourney.PIP_SEEN,false)) {
            getSharedPreferences(HomeJourney.PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(HomeJourney.PIP_SEEN, true).apply();
            homeState.historyChanged(homeOwner);
        }
        setCompactUi(inPip);
        pipActionState=-1;
        syncPipActions();
        tracePip("mode="+inPip);
    }

    private void setCompactUi(boolean compact) {
        compactUi=compact;
        syncKeepScreenOn();
        if (root == null) return;
        PreviewChrome.compact(root,compact,useDepth);
        renderViewfinderChrome(state.phase==SessionState.Phase.LIVE && state.fresh(SystemClock.elapsedRealtime()));
        root.requestApplyInsets();
        if (compact) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        renderRecovery();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (texture != null) texture.post(this::transform);
    }

    private void createWindow() {
        stopTrackingPipBounds();submittedPipBounds=null;submittedPipRotation=-1;
        previewGeometry = null;
        displayedDepthGuide = null;
        root=(LinearLayout)getLayoutInflater().inflate(R.layout.activity_preview,
            (ViewGroup)findViewById(android.R.id.content), false);
        titleView=root.findViewById(R.id.preview_title);
        texture=root.findViewById(R.id.camera_preview);texture.setOpaque(false);
        TextureView source=texture;
        pipBoundsListener=()->{if(texture==source){transform();syncPipSourceBounds();}};
        root.getViewTreeObserver().addOnGlobalLayoutListener(pipBoundsListener);
        cover=root.findViewById(R.id.preview_cover);
        detectionOverlay=root.findViewById(R.id.detection_overlay);
        announcer=new RiskAnnouncer(root);
        postureHint=root.findViewById(R.id.posture_hint);
        info=root.findViewById(R.id.preview_info);
        depthStatus=root.findViewById(R.id.depth_status);
        depthGuideHint=root.findViewById(R.id.depth_guide_hint);
        depthButton=root.findViewById(R.id.enable_depth);
        depthButton.setOnClickListener(v->chooseDepth());
        root.findViewById(R.id.copy_problem_info).setOnClickListener(v->copyProblemInfo());
        root.findViewById(R.id.viewfinder_toggle).setOnClickListener(v->{
            long now=SystemClock.elapsedRealtime();
            if(!compactUi && !destroyed && checkHealth(now) && state.phase==SessionState.Phase.LIVE && state.fresh(now)) {
                enlargedPreview=!enlargedPreview;render(now);
            }
        });
        refreshDepthAvailability();
        renderDepthGuide();
        recognition=root.findViewById(R.id.preview_recognition);
        recognition.setOnCheckedChangeListener((b,checked)-> {
            detectionNotice=0;
            feedback.clear();detectionOverlay.clear();announcer.clear();pendingSpeak=false;analysisEpoch++;cancelAnalysis();
            if(checked && (!LocalDetector.AVAILABLE || state.phase!=SessionState.Phase.LIVE)) { b.setChecked(false);return; }
            state.recognition=checked;
            if(checked){diagnostics.clearDetection();prepareAnalysis(SystemClock.elapsedRealtime());}
            if(!checked) { if(sound!=null)sound.setChecked(false); silence();if(info!=null)info.setText(R.string.info_recognition_off); }else if(info!=null)info.setText(R.string.info_recognition_preparing);
            renderDepthGuide();
            render(SystemClock.elapsedRealtime());
        });
        sound=root.findViewById(R.id.preview_sound);
        vibration=root.findViewById(R.id.preview_vibration);
        vibration.setChecked(getSharedPreferences(REMINDERS_PREFERENCES,MODE_PRIVATE).getBoolean(VIBRATION_PREFERENCE,true));
        haptics.setEnabled(vibration.isChecked());
        ((RiskLegendLayout)root.findViewById(R.id.risk_legend)).setVibrationEnabled(vibration.isChecked());
        vibration.setOnCheckedChangeListener((button,checked)->{
            haptics.setEnabled(checked);
            getSharedPreferences(REMINDERS_PREFERENCES,MODE_PRIVATE).edit().putBoolean(VIBRATION_PREFERENCE,checked).apply();
            ((RiskLegendLayout)root.findViewById(R.id.risk_legend)).setVibrationEnabled(checked);
        });
        Button stop=(Button)root.findViewById(R.id.preview_stop);
        stop.setBackgroundTintList(null);
        stop.setOnClickListener(v->stopPreview());
        pipButton=root.findViewById(R.id.enter_pip);
        pipButton.setBackgroundTintList(null);
        pipButton.setOnClickListener(v->enterPip());
        restartButton=root.findViewById(R.id.restart_preview);
        restartButton.setBackgroundTintList(null);
        restartButton.setOnClickListener(v->recoverPreview());
        sound.setOnCheckedChangeListener((button, checked) -> {
            ++soundRevision;
            if(checked)ensureToneSession();else stopTone();
            syncPipActions();
        });
        TextureView observedTexture=texture;
        observedTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h) { if(texture==observedTexture)openCamera(s); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h) { if(texture==observedTexture)transform(); }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { if(texture==observedTexture && !destroyed && (state.phase==SessionState.Phase.STARTING || state.phase==SessionState.Phase.LIVE)) fail("显示 Surface 已销毁：不可用");return true; }
            public void onSurfaceTextureUpdated(SurfaceTexture s) {
                if(texture!=observedTexture || readingTexture || destroyed || (state.phase!=SessionState.Phase.STARTING && state.phase!=SessionState.Phase.LIVE))return;
                long now=SystemClock.elapsedRealtime();
                if(!checkHealth(now))return;
                state.display(now);state.promote(now);syncTicket();
                if(state.fresh(now) && !submitBrightness(now))return;
                long checkedAt=SystemClock.elapsedRealtime();
                if(!checkHealth(checkedAt))return;
                render(checkedAt);
                if(state.phase==SessionState.Phase.LIVE && state.recognition
                    && (useDepth || now-sampledAt>=500 && !worker.busy()))queueReadback(now);
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
        syncPipSourceBounds();
        // 变换后旧结果不再与当前画面对应；等待新显示帧重新采样。
        feedback.clear();detectionOverlay.clear();announcer.clear();pendingSpeak=false;silence();analysisEpoch++;
        if(state.recognition && analysisTicket!=null) {
            worker.discard(analysisTicket);analysisHasResult=false;sampledAt=0;
            state.analysisAt=SystemClock.elapsedRealtime();syncTicket();requestAnalysisPreparation();
        } else {cancelAnalysis();if(state.recognition)prepareAnalysis(SystemClock.elapsedRealtime());}
    }
    static BitmapFrame samplePreview(TextureView view,PreviewGeometry geometry) {
        // getBitmap 复制的是 SurfaceTexture 内容，不包含 view 的 fit-center Matrix。
        // 保留系统已补偿 sensor 的天然比例；display 补偿在 worker 的输入画布完成。
        Bitmap natural=view.getBitmap(geometry.sampleWidth,geometry.sampleHeight);if(natural==null)return null;
        return new BitmapFrame(natural,geometry.displayDegrees);
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
                    if(now-capturedAt>500){fail("增强画面已过期，请重新开启");return;}
                    if(!checkHealth(now))return;
                    state.capture(timestamp,capturedAt);syncTicket();depthSnapshot=depth;
                    renderDepthStatus(now);
                    renderDepthGuide();
                }
                public void failed(String reason){if(!destroyed && token==state.generation){useDepth=false;fail(RecoveryGuide.Reason.DEPTH,reason);refreshDepthAvailability();}}
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
            if(selected==null) { fail(RecoveryGuide.Reason.UNSUPPORTED,"没有支持 640×480 的后置摄像头：不可用");return; }
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
                                        captureFrame(token,stamp,SystemClock.elapsedRealtime());
                                    }
                                },main);
                            } catch(CameraAccessException|RuntimeException e) { fail("无法开始采集：不可用"); }
                        }
                        public void onConfigureFailed(CameraCaptureSession s) { s.close();if(token==state.generation)fail("摄像头配置失败：不可用"); }
                    },main); } catch(CameraAccessException|RuntimeException e) { fail("建立采集会话失败：不可用"); }
                }
                public void onDisconnected(CameraDevice device) {device.close();if(token==state.generation)fail(RecoveryGuide.Reason.CAMERA_BUSY,"摄像头断开或被其他应用占用：不可用");}
                public void onError(CameraDevice device,int error) {device.close();if(token==state.generation)fail(RecoveryGuide.Reason.CAMERA_BUSY,"摄像头错误 / 被占用（"+error+"）：不可用");}
            },main);
        } catch(CameraAccessException|RuntimeException e) { fail(RecoveryGuide.Reason.CAMERA_BUSY,"摄像头不可用：检查占用、权限、隐私开关"); }
    }
    private void syncTicket(){if(analysisTicket!=null){analysisTicket.captureAt=state.captureAt;analysisTicket.displayAt=state.displayAt;analysisTicket.analysisAt=state.analysisAt;}}
    private void observeBrightness(long frameAt) {
        if(brightnessReadback==null) {
            long generation=state.generation;
            brightnessReadback=new BrightnessReadback((owner,expired,status,total,checkedFrameAt)->{
                if(destroyed || owner!=brightnessReadback || generation!=state.generation
                    || state.phase!=SessionState.Phase.STARTING && state.phase!=SessionState.Phase.LIVE)return;
                long now=SystemClock.elapsedRealtime();
                if(!checkHealth(now))return;
                BrightnessTiming.Sample timing=owner.snapshot(now);
                if(timing.wallMs>=200)android.util.Log.w("WalkBuddyTiming","Brightness asyncWallMs="+timing.wallMs
                    +" callerCpuMs="+timing.cpuMs+" queueMs="+timing.queueMs+" deliveryMs="+timing.deliveryMs);
                if(expired || status!=android.view.PixelCopy.SUCCESS){fail(getString(R.string.preview_readback_failed));return;}
                if(total<16*12*3*3){fail(RecoveryGuide.Reason.DARK,"画面过暗或隐私占位：不可用（仅白天）");return;}
                if(state.phase==SessionState.Phase.STARTING) {
                    state.imageChecked(generation,checkedFrameAt,now);state.promote(now);syncTicket();render(now);
                }
            });
        }
        brightnessReadback.observe(frameAt);
    }
    private boolean submitBrightness(long frameAt) {
        try {
            observeBrightness(frameAt);
            // Wrap and enqueue only. PixelCopy and getPixels execute on the worker.
            if(brightnessReadback.ready())brightnessReadback.request(texture,frameAt);
            return true;
        } catch(RuntimeException error) {
            android.util.Log.w("WalkBuddyTiming","Brightness submission error="+error.getClass().getSimpleName());
            fail(getString(R.string.preview_readback_failed));return false;
        }
    }
    private void queueReadback(long frameAt) {
        if(pendingReadback!=null || texture==null)return;
        TextureView source=texture;long token=state.generation,epoch=analysisEpoch;
        long queuedAt=SystemClock.elapsedRealtime();
        pendingReadback=new Runnable(){public void run(){
            boolean timed=false,reading=false;
            try {
                if(pendingReadback!=this || destroyed || token!=state.generation || texture!=source || state.phase!=SessionState.Phase.LIVE)return;
                long now=SystemClock.elapsedRealtime();
                lastReadbackQueueMs=now-queuedAt;
                readbackTiming.begin(token,SystemClock.elapsedRealtimeNanos(),Debug.threadCpuTimeNanos());
                timed=true;
                if(!checkHealth(now))return;
                if(now-frameAt>1000)return; // Keep the original observation time; a delayed task cannot refresh it.
                if(state.recognition && epoch==analysisEpoch) {
                    // The remaining model getBitmap path must not count as a displayed frame.
                    readingTexture=reading=true;
                    if(useDepth)processDepth(now);
                    if(frameAt-sampledAt>=500 && !worker.busy()) {
                        if(worker.ready(analysisTicket))sample(frameAt);else requestAnalysisPreparation();
                    }
                }
            } catch(RuntimeException error) {
                android.util.Log.w("WalkBuddyTiming","Readback error="+error.getClass().getSimpleName());
                fail(getString(R.string.preview_readback_failed));
            } finally {
                if(timed)readbackTiming.finish(token,SystemClock.elapsedRealtimeNanos(),Debug.threadCpuTimeNanos());
                if(timed && readbackTiming.completedAtLeast(token,200)) {
                    ReadbackTiming.Sample timing=readbackTiming.snapshot(token,0,0);
                    android.util.Log.w("WalkBuddyTiming","Readback taskWallMs="+timing.wallMs+" taskCpuMs="+timing.cpuMs
                        +" brightnessWallMs="+timing.brightnessMs+" brightnessCpuMs="+timing.brightnessCpuMs);
                }
                if(reading)readingTexture=false;
                if(pendingReadback==this)pendingReadback=null;
            }
        }};
        // Model getBitmap must run after drawing. Brightness only queues its worker there.
        main.post(pendingReadback);
    }
    private void captureFrame(long token,long stamp,long receivedAt) {
        if(destroyed || token!=state.generation)return;
        long now=SystemClock.elapsedRealtime(),nanos=SystemClock.elapsedRealtimeNanos();
        if(!checkHealth(now))return;
        long captureAt=state.captureAt,displayAt=state.displayAt,analysisAt=state.analysisAt;
        CameraFrames.Decision decision=CameraFrames.accept(state,token,stamp,receivedAt,now,nanos,realtimeTimestamp);
        if(decision==CameraFrames.Decision.DEFER) {
            if(pendingCapture!=null)return; // One pending metadata observation, never a queue of old frames.
            deferredCaptureCount++;
            pendingCapture=()->{pendingCapture=null;captureFrame(token,stamp,receivedAt);};
            main.postDelayed(pendingCapture,CameraFrames.waitMillis(stamp,nanos));
        } else if(decision==CameraFrames.Decision.STARTUP_STALE) {
            if(staleStartupGeneration!=token){staleStartupGeneration=token;android.util.Log.w("WalkBuddyCamera",
                "Discarding stale startup metadata ageNanos="+(nanos-stamp)+"; original startup deadline unchanged");}
        } else if(decision.failure!=null) {
            android.util.Log.w("WalkBuddyCamera","Rejected capture ageNanos="+(nanos-stamp)
                +" monotonicAgeNanos="+(System.nanoTime()-stamp)+" captureGapMs="+(now-captureAt)
                +" displayGapMs="+(now-displayAt)+" analysisGapMs="+(now-analysisAt)+" pip="+isInPictureInPictureMode());
            fail(decision.failure);
        } else if(decision==CameraFrames.Decision.ACCEPTED) {
            if(pendingCapture!=null){main.removeCallbacks(pendingCapture);pendingCapture=null;}
            syncTicket();
        }
    }
    private void cancelAnalysis(){analysisReady=analysisHasResult=false;if(analysisTicket!=null){worker.cancel(analysisTicket);analysisTicket=null;}}
    private void prepareAnalysis(long now) {
        state.analysisAt=now;analysisTicket=new InferenceWorker.Ticket(now);syncTicket();sampledAt=0;
        requestAnalysisPreparation();
    }
    static LocalDetector createWorkerDetector(Context app,int priority) throws java.io.IOException {
        return createWorkerDetector(app,priority,LocalDetector.defaultThreads(),false);
    }
    static LocalDetector createWorkerDetector(Context app,int priority,int threads,boolean traceAll) throws java.io.IOException {
        android.os.Process.setThreadPriority(priority);
        long started=SystemClock.elapsedRealtime(),cpuStarted=Debug.threadCpuTimeNanos();
        LocalDetector detector=new LocalDetector(app,threads,traceAll);
        long finished=SystemClock.elapsedRealtime(),cpuMs=(Debug.threadCpuTimeNanos()-cpuStarted)/1_000_000;
        String allowed="unknown";
        try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.FileReader("/proc/thread-self/status"))) {
            String line;while((line=reader.readLine())!=null)if(line.startsWith("Cpus_allowed_list:")){allowed=line.substring(line.indexOf(':')+1).trim();break;}
        } catch(java.io.IOException|SecurityException ignored){/* Optional scheduling metadata. */}
        android.util.Log.i("WalkBuddyAnalysis","Model ready initMs="+(finished-started)+" initWorkerCpuMs="+cpuMs
            +" priority="+android.os.Process.getThreadPriority(android.os.Process.myTid())+" allowedCpus="+allowed+" threads="+threads);
        return detector;
    }
    private void requestAnalysisPreparation() {
        long token=state.generation,epoch=analysisEpoch;InferenceWorker.Ticket ticket=analysisTicket;
        if(ticket==null)return;
        worker.prepare(ticket,(unused,error)->main.post(()->{
            if(destroyed || ticket.cancelled || ticket!=analysisTicket || epoch!=analysisEpoch || token!=state.generation)return;
            if(!checkHealth(SystemClock.elapsedRealtime()) || ticket.cancelled)return;
            if(error!=null){pauseDetection(R.string.info_detection_failed);return;}
            analysisReady=true;
        }));
    }
    private void sample(long frameAt) {
        InferenceWorker.Ticket ticket=analysisTicket;long token=state.generation,epoch=analysisEpoch;
        TextureView source=texture;PreviewGeometry geometry=previewGeometry;
        if(ticket==null || !ticket.allowed(SystemClock.elapsedRealtime()) || source==null || geometry==null)return;
        long copyStarted=SystemClock.elapsedRealtime();
        BitmapFrame frame=samplePreview(source,geometry);if(frame==null){fail("无法读取分析帧：不可用");return;}
        long copyMs=SystemClock.elapsedRealtime()-copyStarted;lastSampleCopyMs=copyMs;
        if(copyMs>=200)android.util.Log.w("WalkBuddyAnalysis","Slow camera copyMs="+copyMs+" pip="+isInPictureInPictureMode());
        long copiedAt=SystemClock.elapsedRealtime();
        if(destroyed || token!=state.generation || epoch!=analysisEpoch || ticket!=analysisTicket || texture!=source
            || state.phase!=SessionState.Phase.LIVE || !ticket.allowed(copiedAt) || copiedAt-frameAt>1000){frame.close();return;}
        sampledAt=frameAt;
        worker.submit(ticket,frame,frameAt,(result,error)->{
            long completedAt=SystemClock.elapsedRealtime();
            main.post(()->{
                long now=SystemClock.elapsedRealtime();
                if(destroyed || ticket.cancelled || ticket!=analysisTicket || epoch!=analysisEpoch || token!=state.generation)return;
                lastResultDeliveryMs=now-completedAt;
                if(!checkHealth(now) || ticket.cancelled)return;
                if(!state.acceptResult(token,frameAt,now))return;
                if(error!=null){pauseDetection(R.string.info_detection_failed);return;}
                state.analysisAt=now;syncTicket();
                analysisHasResult=true;
                boolean notify=feedback.basic(result.detections,frameAt,now);
                showFeedback(now);
                if(notify)alert();
            });
        });
    }
    private void alert(){
        long now=SystemClock.elapsedRealtime();
        if(!state.recognition || state.expired(now))return;
        Detection.Risk risk=RiskHaptics.strongest(feedback.visible(now).detections);
        haptics.update(risk);
        toneWorker.observe(activeTone,risk);
        if(risk==Detection.Risk.CAPTURED)return;
        pendingSpeak=true;
        haptics.alert(risk);
        if(sound.isChecked()) {
            TonePlayer.Session session=ensureToneSession();
            toneWorker.observe(session,risk);
            toneWorker.play(session,feedback.validUntil(now,risk));
        }
    }
    private boolean checkHealth(long now) {
        PreviewHealth.Decision decision=PreviewHealth.check(state,now);
        if(decision==PreviewHealth.Decision.STOP_PREVIEW){fail("画面已过期：不可用，请重新开启");return false;}
        if(brightnessReadback!=null && brightnessReadback.expired(now)){fail(getString(R.string.preview_readback_failed));return false;}
        if(decision==PreviewHealth.Decision.STOP_DETECTION) {
            pauseDetection(R.string.info_detection_timeout);
            return state.phase==SessionState.Phase.LIVE;
        }
        return true;
    }
    private void pauseDetection(int message) {
        if(destroyed)return;
        if(!getSystemService(PowerManager.class).isInteractive() || getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            fail(RecoveryGuide.Reason.LOCKED,"锁屏：摄像头已停止，请主动重新开启");return;
        }
        long now=SystemClock.elapsedRealtime();
        if(state.phase!=SessionState.Phase.LIVE || !state.fresh(now)){fail("画面已过期：不可用，请重新开启");return;}
        android.util.Log.i("WalkBuddyAnalysis","Detection stopped; preview continues message="+getString(message));
        if(message==R.string.info_detection_failed || message==R.string.info_detection_timeout)
            diagnostics.detection(diagnosticSnapshot(message==R.string.info_detection_timeout?"DETECTION_TIMEOUT":"DETECTION_ERROR",getString(message),now));
        recognition.setChecked(false);
        state.recognition=false;state.analysisAt=0;cancelAnalysis();
        feedback.clear();detectionOverlay.clear();announcer.clear();pendingSpeak=false;silence();
        sound.setChecked(false);detectionNotice=message;
        render(now);
        ((ScrollView)root.findViewById(R.id.preview_controls)).scrollTo(0,0);
    }
    private void silence() {
        if(haptics!=null)haptics.clear();
        stopTone();
    }
    private void stopTone() {
        TonePlayer.Session retired=activeTone;activeTone=null;
        if(retired!=null)toneWorker.cancel(retired);
    }
    private TonePlayer.Session ensureToneSession() {
        long now=SystemClock.elapsedRealtime();
        if(destroyed || !sound.isChecked() || !state.recognition || state.phase!=SessionState.Phase.LIVE
            || state.expired(now) || analysisTicket==null)return null;
        if(activeTone==null || activeTone.frames!=analysisTicket) {
            stopTone();
            activeTone=new TonePlayer.Session(analysisTicket,(session,error)->main.post(()->{
                if(destroyed || activeTone!=session || !sound.isChecked())return;
                sound.setChecked(false);
                Toast.makeText(this,R.string.info_sound_unavailable,Toast.LENGTH_LONG).show();
            }));
            toneWorker.prepare(activeTone);
        }
        return activeTone;
    }
    private void render(long now) {
        syncKeepScreenOn();
        if(cover==null)return;
        boolean live=state.phase==SessionState.Phase.LIVE && state.fresh(now);
        if (live && !previewSeen) {
            previewSeen = true;
            getSharedPreferences(HomeJourney.PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(HomeJourney.PREVIEW_SEEN, true).apply();
            homeState.historyChanged(homeOwner);
        }
        cover.setVisibility(live?View.GONE:View.VISIBLE);
        if(!live && state.phase==SessionState.Phase.STARTING) setTextIfChanged(cover,
            getText(state.fresh(now)?R.string.preview_checking_image:R.string.preview_starting));
        recognition.setEnabled(live && LocalDetector.AVAILABLE);
        sound.setEnabled(live && state.recognition);
        vibration.setEnabled(live);
        if(live && restoreDetection){boolean enableSound=restoreSound;restoreDetection=restoreSound=false;recognition.setChecked(true);sound.setChecked(enableSound);}
        pipButton.setEnabled(live);
        boolean restart=state.phase==SessionState.Phase.UNAVAILABLE || state.phase==SessionState.Phase.STOPPED;
        PreviewChrome.showRestart(root,restart);
        PreviewRecovery.problemInfo(root,diagnostics.available());
        depthButton.setEnabled(live);
        homeState.publish(homeOwner,state.phase,state.reason,state.recognition,detectionNotice,homeNotice);
        renderViewfinderChrome(live);
        if(live){setTextIfChanged(cover,"");if(!state.recognition)setTextIfChanged(info,getText(detectionNotice!=0?detectionNotice:R.string.info_recognition_off));}
        if(titleView!=null)PreviewChrome.title(titleView,PreviewStatus.heading(state.phase,live,state.recognition));
        renderDepthGuide();
        renderRecovery();
        syncPipActions();
    }
    private boolean enlargedPreview;
    private void renderViewfinderChrome(boolean live) {
        PreviewChrome.viewfinder(root,compactUi,live,enlargedPreview);
        PreviewChrome.detectionStatus(root,live,state.recognition,detectionNotice!=0,compactUi,
            live && enlargedPreview && !compactUi);
    }
    private void renderRecovery() {
        if(root==null)return;
        if(state.phase==SessionState.Phase.UNAVAILABLE || state.phase==SessionState.Phase.STOPPED)
            PreviewRecovery.show(root,RecoveryGuide.resolve(recoveryReason,
                checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED),compactUi);
        else PreviewRecovery.clear(root);
    }
    private void fail(String message) {
        fail(RecoveryGuide.Reason.GENERAL,message);
    }
    private void fail(RecoveryGuide.Reason reason,String message) {
        if(destroyed)return;
        enlargedPreview=false;
        boolean observing=state.phase==SessionState.Phase.STARTING || state.phase==SessionState.Phase.LIVE;
        if(reason!=RecoveryGuide.Reason.INSTALLING && reason!=RecoveryGuide.Reason.SWITCHING
            && (observing || reason!=RecoveryGuide.Reason.HIDDEN && reason!=RecoveryGuide.Reason.LOCKED))
            diagnostics.preview(diagnosticSnapshot(reason.name(),message,SystemClock.elapsedRealtime()));
        main.removeCallbacks(watchdog);
        if(state.phase==SessionState.Phase.STARTING || state.phase==SessionState.Phase.LIVE) {
            long now=SystemClock.elapsedRealtime();
            android.util.Log.w("WalkBuddyTiming","Preview stopped reason="+reason+" phase="+state.phase
                +" recognition="+state.recognition+" captureGapMs="+(state.captureAt>0?now-state.captureAt:-1)
                +" displayGapMs="+(state.displayAt>0?now-state.displayAt:-1)
                +" analysisGapMs="+(state.analysisAt>0?now-state.analysisAt:-1)
                +" sampleAgeMs="+(sampledAt>0?now-sampledAt:-1)+" queueMs="+lastReadbackQueueMs
                +" copyMs="+lastSampleCopyMs+" deliveryMs="+lastResultDeliveryMs
                +" workerBusy="+(worker!=null && worker.busy())+" pip="+isInPictureInPictureMode());
        }
        recoveryReason=reason;
        detectionNotice=0;
        restoreDetection=restoreSound=false;
        cancelAnalysis();state.unavailable(message);feedback.clear();if(announcer!=null)announcer.clear();pendingSpeak=false;silence();
        if(detectionOverlay!=null)detectionOverlay.clear();
        stopPosture();
        if(recognition!=null)recognition.setChecked(false);
        // 先不透明遮挡，再移除纹理，从视图树彻底清除最后一帧。
        if(cover!=null) cover.setVisibility(View.VISIBLE);
        closeCamera();
        if(texture!=null) {
            TextureView retired=texture;texture=null;
            retired.setSurfaceTextureListener(null);
            // SurfaceTextureUpdated runs inside TextureView.draw(). Detaching here would destroy
            // the TextureLayer that this draw still uses. The opaque cover/camera stop are immediate.
            main.post(()->{if(retired.getParent() instanceof ViewGroup)((ViewGroup)retired.getParent()).removeView(retired);});
        }
        if(info!=null)info.setText(R.string.info_stopped);
        if(root!=null){render(SystemClock.elapsedRealtime());renderDepthGuide();}
        if(root!=null)((ScrollView)root.findViewById(R.id.preview_controls)).scrollTo(0,0);
        homeState.failure(homeOwner,message);
    }
    private String detectionNoticeText(){return getString(detectionNotice);}
    private SessionDiagnostics.Snapshot diagnosticSnapshot(String category,String message,long now) {
        boolean active=state.phase==SessionState.Phase.STARTING || state.phase==SessionState.Phase.LIVE;
        SessionDiagnostics.Pipeline pipeline=new SessionDiagnostics.Pipeline(active && analysisReady,active && analysisHasResult,
            worker!=null && worker.busy(),active?SessionDiagnostics.age(now,sampledAt):-1,
            active?lastReadbackQueueMs:-1,active?lastSampleCopyMs:-1,active?lastResultDeliveryMs:-1,
            active?readbackTiming.snapshot(state.generation,SystemClock.elapsedRealtimeNanos(),Debug.threadCpuTimeNanos()):ReadbackTiming.UNKNOWN,
            active && brightnessReadback!=null?brightnessReadback.snapshot(now):BrightnessTiming.UNKNOWN);
        return new SessionDiagnostics.Snapshot(category,message,state,now,useDepth || depthCamera!=null,
            isInPictureInPictureMode(),sound!=null && sound.isChecked(),haptics!=null && haptics.enabled(),pipeline);
    }
    private String problemInfoText() {
        return diagnostics.report(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,
            Build.MANUFACTURER,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT);
    }
    private void copyProblemInfo() {
        String report=problemInfoText();
        if(report.isEmpty())return;
        try {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard==null)throw new IllegalStateException("Clipboard unavailable");
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.problem_info_title),report));
            if(Build.VERSION.SDK_INT<33)Toast.makeText(this,R.string.problem_info_copied,Toast.LENGTH_SHORT).show();
        } catch(RuntimeException error){Toast.makeText(this,R.string.problem_info_copy_failed,Toast.LENGTH_SHORT).show();}
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
        if(!resumed || isInPictureInPictureMode() || state.phase!=SessionState.Phase.LIVE)return;
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
            if(ArCoreApk.getInstance().requestInstall(this,userRequested)==ArCoreApk.InstallStatus.INSTALL_REQUESTED){pendingDepthInstall=true;fail(RecoveryGuide.Reason.INSTALLING,"等待增强服务安装；返回后继续");return;}
            switchDepth(true);restoreDetection=installDetection;restoreSound=installSound;
        }catch(Exception|LinkageError error){pendingDepthInstall=false;useDepth=false;fail(RecoveryGuide.Reason.DEPTH,"增强服务暂不可用，请继续基础模式");refreshDepthAvailability();}
    }
    private void recoverPreview() {
        tracePip("recover-request");
        if(!resumed || destroyed || isInPictureInPictureMode())return;
        RecoveryGuide.Prompt prompt=RecoveryGuide.resolve(recoveryReason,
            checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED);
        switch(prompt.action) {
            case SETTINGS:
                try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:"+getPackageName())));}
                catch(RuntimeException error){Toast.makeText(this,R.string.settings_unavailable,Toast.LENGTH_LONG).show();}
                return;
            case HOME: finish();return;
            case WAIT: return;
            case BASIC:
                if(useDepth || recoveryReason==RecoveryGuide.Reason.DEPTH)switchDepth(false);
                else {useDepth=false;startPreview();}
                return;
            default: startPreview();
        }
    }
    private void switchDepth(boolean enabled) {
        boolean detection=state.recognition,audio=sound.isChecked();
        fail(RecoveryGuide.Reason.SWITCHING,"正在切换观察模式");useDepth=enabled;restoreDetection=detection;restoreSound=audio;
        long token=state.generation;
        ArDepthCamera.afterRelease(()->main.postDelayed(()->{
            if(token!=state.generation)return;
            startPreview();
            // Losing focus/locking can reject the delayed start without changing generation.
            // Leave a manual recovery action instead of a permanently disabled waiting button.
            if(token==state.generation)fail(RecoveryGuide.Reason.HIDDEN,"切换已暂停，请主动重新开启预览");
        },250));
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
        String message=state.recognition && !analysisHasResult && visible.detections.isEmpty()
            ?getString(analysisReady?R.string.info_recognition_on:R.string.info_recognition_preparing):visible.message;
        detectionOverlay.show(visible.detections,previewGeometry);setTextIfChanged(info,message);
        Detection.Risk worst=RiskHaptics.strongest(visible.detections);
        haptics.update(worst);
        toneWorker.observe(activeTone,worst);
        boolean alert=pendingSpeak;pendingSpeak=false;
        announcer.update(visible.message,worst,alert,now);
    }
    private void renderDepthGuide() {
        if(root==null || depthGuideHint==null)return;
        PreviewChrome.depth(root,useDepth);
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
        setTextIfChanged(root.findViewById(R.id.pip_depth_status),progress.compactHint());
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
        if(brightnessReadback!=null){brightnessReadback.close();brightnessReadback=null;}
        stopTrackingPipBounds();
        if(pendingReadback!=null){main.removeCallbacks(pendingReadback);pendingReadback=null;}
        if(pendingCapture!=null){main.removeCallbacks(pendingCapture);pendingCapture=null;}
        if(depthCamera!=null){depthCamera.stop();depthCamera=null;}depthSnapshot=null;
        if(session!=null) {session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(surface!=null){surface.release();surface=null;}
    }

}
