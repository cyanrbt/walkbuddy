package com.cyanrbt.walkbuddy;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.RemoteAction;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.app.KeyguardManager;
import android.view.View;
import android.view.TextureView;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Emulator-only real Camera2/PiP lifecycle checks, using the configured virtual rear camera. */
final class PipProbe {
    /** Independent no-model reproduction: fault in PiP, expand, restart, immediately re-enter. */
    static void runRecovery(Instrumentation runner) {
        runRecovery(runner,false);
    }

    static void runRecovery(Instrumentation runner,boolean trace) {
        Bundle result=new Bundle();Activity home=null;PreviewActivity preview=null;
        StringBuilder report=new StringBuilder();int code=Activity.RESULT_CANCELED;
        PreviewMainTrace tracing=null;
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("PiP recovery probe is restricted to emulators");
            if(trace){tracing=new PreviewMainTrace(runner);runner.runOnMainSync(()->BrightnessReadback.traceRequests(true));}
            if(!PipSupport.available(runner.getTargetContext()))throw new IllegalStateException("System PiP is unavailable");
            try(java.io.InputStream fixture=runner.getContext().getAssets().open("wake-input-inactive.txt")) {
                report.append("INPUT_WINDOW_RULES_PASS cases=").append(InputWindowState.verifyWakeFixture(
                    new String(fixture.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8))).append('\n');
            }
            try(java.io.InputStream fixture=runner.getContext().getAssets().open("restore-down-paused.txt")) {
                report.append("REJECTED_RESTORE_TOUCH_RULES_PASS cases=").append(RejectedTouch.verifyFixture(
                    new String(fixture.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8))).append('\n');
            }
            shell(runner,"pm grant "+runner.getTargetContext().getPackageName()+" android.permission.CAMERA");
            home=runner.startActivitySync(new Intent(runner.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity launcher=home;
            Instrumentation.ActivityMonitor monitor=runner.addMonitor(PreviewActivity.class.getName(),null,false);
            try {
                tap(runner,launcher,R.id.start);
                preview=(PreviewActivity)runner.waitForMonitorWithTimeout(monitor,7000);
            } finally {runner.removeMonitor(monitor);}
            if(preview==null)throw new AssertionError("Recovery preview did not open");
            PreviewActivity host=preview;
            await(runner,()->live(host),"recovery Camera2 LIVE");
            tap(runner,host,R.id.enter_pip);
            await(runner,host::isInPictureInPictureMode,"recovery initial PiP");
            for(int cycle=0;cycle<4;cycle++) {
                if(cycle==2) {
                    await(runner,()->(boolean)field(launcher,"visible"),"home before recovery lock sequence");
                    tap(runner,launcher,R.id.start);
                    await(runner,()->!host.isInPictureInPictureMode() && (boolean)field(host,"resumed"),"expand before recovery lock");
                    shell(runner,"input keyevent KEYCODE_SLEEP");
                    await(runner,()->state(host).phase==SessionState.Phase.UNAVAILABLE
                        && !host.getSystemService(PowerManager.class).isInteractive() && !(boolean)field(host,"resumed"),"recovery screen off");
                    shell(runner,"input keyevent KEYCODE_WAKEUP");shell(runner,"wm dismiss-keyguard");
                    await(runner,()->(boolean)field(host,"resumed") && host.getSystemService(PowerManager.class).isInteractive()
                        && !host.getSystemService(KeyguardManager.class).isKeyguardLocked(),"recovery wake and unlock");
                    awaitWakeInputReady(runner,host);
                    read(runner,()->{assertReleased(host);return true;});
                    tap(runner,host,R.id.restart_preview);
                    await(runner,()->live(host),"manual camera restart after recovery lock");
                    tap(runner,host,R.id.enter_pip);
                    await(runner,host::isInPictureInPictureMode,"PiP after recovery lock");
                    report.append("PASS recovery lock/wake precondition\n");
                }
                await(runner,()->(boolean)field(launcher,"visible"),"home visible behind PiP");
                read(runner,()->{
                    if(trace)android.os.Trace.beginSection("wb_test:forced_expiry");
                    try {
                    Handler handler=(Handler)field(host,"main");Runnable watchdog=(Runnable)field(host,"watchdog");
                    handler.removeCallbacks(watchdog);
                    state(host).captureAt=android.os.SystemClock.elapsedRealtime()-1201;
                    watchdog.run();assertReleased(host);assertNotPolling(host);
                    return true;
                    } finally {if(trace)android.os.Trace.endSection();}
                });
                tap(runner,launcher,R.id.start);
                await(runner,()->!host.isInPictureInPictureMode() && (boolean)field(host,"resumed"),"recovery expansion");
                read(runner,()->{assertReleased(host);return true;});
                // PiP expansion can resume the Activity before input dispatch has settled.
                awaitPreviewInputReady(runner,host,"PiP expansion");
                tap(runner,host,R.id.restart_preview);
                await(runner,()->live(host),"recovery manual restart");
                int iteration=cycle;
                read(runner,()->{
                    View button=host.findViewById(R.id.enter_pip);
                    report.append("RECOVERY_BEFORE_INPUT cycle=").append(iteration).append(" resumed=").append(field(host,"resumed"))
                        .append(" phase=").append(state(host).phase).append(" fresh=").append(state(host).fresh(android.os.SystemClock.elapsedRealtime()))
                        .append(" focus=").append(host.hasWindowFocus()).append(" enabled=").append(button.isEnabled())
                        .append(" attached=").append(button.isAttachedToWindow()).append(" shown=").append(button.isShown())
                        .append(" listener=").append(button.hasOnClickListeners()).append(" current=").append(button==field(host,"pipButton"))
                        .append(" input=touch").append('\n');
                    return true;
                });
                int[] fullSize=read(runner,()->{View root=(View)field(host,"root");return new int[]{root.getWidth(),root.getHeight()};});
                tap(runner,host,R.id.enter_pip);
                await(runner,host::isInPictureInPictureMode,"immediate PiP re-entry cycle="+cycle);
                long started=android.os.SystemClock.elapsedRealtime();
                await(runner,()->live(host) && state(host).captureAt>started+300 && state(host).displayAt>started+300,"recovery PiP frames advance");
                read(runner,()->{View root=(View)field(host,"root"),warning=host.findViewById(R.id.detection_status);
                    android.graphics.Rect rect=new android.graphics.Rect();boolean visible=warning.getGlobalVisibleRect(rect);
                    report.append("PREVIEW_ONLY_RECOVERY_TRANSITION cycle=").append(iteration).append(" root=").append(root.getWidth()).append('x').append(root.getHeight())
                        .append(" layoutRequested=").append(root.isLayoutRequested()).append(" warning=").append(warning.getWidth()).append('x').append(warning.getHeight())
                        .append(" visible=").append(visible).append(" rect=").append(rect).append(" warningLayoutRequested=").append(warning.isLayoutRequested()).append('\n');return true;});
                await(runner,()->{
                    View root=(View)field(host,"root");
                    return root.getWidth()>0 && root.getHeight()>0 && root.getWidth()<fullSize[0] && root.getHeight()<fullSize[1]
                        && !root.isLayoutRequested() && warningReady(host);
                },"recovery actual compact root and warning layout");
                long settled=android.os.SystemClock.elapsedRealtime();
                await(runner,()->live(host) && state(host).captureAt>settled+150 && state(host).displayAt>settled+150,"recovery fresh frames after actual shrink");
                read(runner,()->{assertDefaultPreviewOnly(host,true);return true;});
                read(runner,()->{View root=(View)field(host,"root");report.append("PREVIEW_ONLY_RECOVERY_GEOMETRY cycle=").append(iteration)
                    .append(" full=").append(fullSize[0]).append('x').append(fullSize[1]).append(" compact=").append(root.getWidth()).append('x').append(root.getHeight())
                    .append(" fresh_after_shrink=true\n");return true;});
                report.append("PASS immediate PiP recovery cycle=").append(cycle).append('\n');
            }
            report.append("PREVIEW_ONLY_RECOVERY_PASS cycles=4; default detection-off notice survives real manual recovery and PiP re-entry\n");
            screenshot(runner,"pip-home-controls.png");
            tap(runner,launcher,R.id.stop);
            await(runner,host::isDestroyed,"native home stop closes PiP");
            read(runner,()->{assertReleased(host);assertNotPolling(host);return true;});
            await(runner,()->launcher.findViewById(R.id.stop).getVisibility()==View.GONE
                && launcher.findViewById(R.id.home_dock).getTop()>launcher.findViewById(R.id.home_scroll).getTop(),"idle home controls return to bottom");
            screenshot(runner,"pip-home-stopped.png");
            result.putString("stream",report+"PIP_RECOVERY_PROBE_PASS cycles=4; native touch return/restart/re-entry before and after lock; native home stop, controls restore\n");
            code=Activity.RESULT_OK;
        } catch(Throwable error) {
            failureEvidence(runner,"pip-recovery-failure",preview);
            result.putString("stream",report+"PIP_RECOVERY_PROBE_FAIL: "+error+"; "+PreviewActivity.report());
        } finally {
            shell(runner,"input keyevent KEYCODE_WAKEUP");
            if(preview!=null){PreviewActivity host=preview;runner.runOnMainSync(host::finish);}
            if(home!=null){Activity host=home;runner.runOnMainSync(host::finish);}
            if(tracing!=null){runner.runOnMainSync(()->BrightnessReadback.traceRequests(false));tracing.close();}
        }
        runner.finish(code,result);
    }

    static void run(Instrumentation runner,Integer profilePriority,Integer profileThreads) {
        Bundle result=new Bundle();Activity home=null;PreviewActivity preview=null;
        StringBuilder report=new StringBuilder();int code=Activity.RESULT_CANCELED;
        InferenceWorker<BitmapFrame,LocalDetector.Result> profiledWorker=null;
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("PiP probe is restricted to emulators");
            if(profilePriority!=null || profileThreads!=null) {
                int priority=profilePriority==null?1:profilePriority;
                int threads=profileThreads==null?LocalDetector.defaultThreads():profileThreads;
                if(priority!=1 && priority!=10)throw new IllegalArgumentException("Unsupported profile priority");
                if(threads!=1 && threads!=2)throw new IllegalArgumentException("Unsupported profile threads");
                Field shared=PreviewActivity.class.getDeclaredField("sharedWorker");shared.setAccessible(true);
                if(shared.get(null)!=null)throw new AssertionError("Profile needs a fresh instrumentation process");
                profiledWorker=new InferenceWorker<>(android.os.SystemClock::elapsedRealtime,
                    ()->PreviewActivity.createWorkerDetector(runner.getTargetContext(),priority,threads,true));
                shared.set(null,profiledWorker);
                report.append(profileThreads==null?"SCHEDULING_PROFILE priority=":"THREAD_PROFILE priority=").append(priority).append(" threads=").append(threads).append(" processors=")
                    .append(Runtime.getRuntime().availableProcessors()).append('\n');
            }
            if(!PipSupport.available(runner.getTargetContext()))throw new IllegalStateException("System PiP is unavailable");
            shell(runner,"pm grant "+runner.getTargetContext().getPackageName()+" android.permission.CAMERA");
            runner.getTargetContext().getSharedPreferences(HomeJourney.PREFERENCES,0).edit().clear().commit();
            home=runner.startActivitySync(new Intent(runner.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity launcher=home;
            read(runner,()->{
                if(launcher.findViewById(R.id.home_step_pip).getContentDescription().toString().contains("已完成"))
                    throw new AssertionError("PiP availability counted as successful experience");
                return true;
            });
            Instrumentation.ActivityMonitor monitor=runner.addMonitor(PreviewActivity.class.getName(),null,false);
            try {
                read(runner,()->launcher.findViewById(R.id.start).performClick());
                preview=(PreviewActivity)runner.waitForMonitorWithTimeout(monitor,7000);
            } finally {runner.removeMonitor(monitor);}
            if(preview==null)throw new AssertionError("Preview did not open from home");
            PreviewActivity host=preview;
            await(runner,()->live(host),"Camera2 LIVE");
            read(runner,()->{
                assertWatching(host);
                assertDefaultPreviewOnly(host,false);
                if(((CheckBox)host.findViewById(R.id.preview_recognition)).isChecked()
                    || ((CheckBox)host.findViewById(R.id.preview_sound)).isChecked())throw new AssertionError("Detection/sound must default off");
                if(!host.getSharedPreferences(HomeJourney.PREFERENCES,0).getBoolean(HomeJourney.PREVIEW_SEEN,false))
                    throw new AssertionError("Fresh camera preview was not recorded");
                if(!host.findViewById(R.id.enable_depth).isEnabled())throw new AssertionError("LIVE camera cannot switch mode");
                return true;
            });
            report.append("PASS Camera2 capture and display are LIVE; default detection/sound off\n");
            read(runner,()->{
                for(int id:new int[]{R.id.preview_recognition,R.id.preview_sound}) {
                    View control=host.findViewById(id);android.graphics.Rect visible=new android.graphics.Rect();
                    if(!control.getGlobalVisibleRect(visible) || visible.height()!=control.getHeight() || visible.width()!=control.getWidth())
                        throw new AssertionError("Initial live toggle is clipped");
                    checkedNode((CheckBox)control);
                }
                if(host.findViewById(R.id.depth_steps).getVisibility()!=View.GONE || host.findViewById(R.id.depth_status).getVisibility()!=View.GONE)
                    throw new AssertionError("Basic camera exposed inactive depth progress");
                return true;
            });
            screenshot(runner,"preview-controls-live.png");
            checkpoint(runner,"camera_live");
            if(profilePriority==null && profileThreads==null)checkPipSourceBounds(runner,host,report);
            if(profilePriority==null && profileThreads==null)screenIdleLifecycle(runner,host,launcher,report);

            // Force expiry from the actual TextureView.draw -> SurfaceTextureUpdated path.
            AtomicBoolean expiredInDraw=new AtomicBoolean();
            AtomicReference<TextureView.SurfaceTextureListener> retiredListener=new AtomicReference<>();
            AtomicReference<SurfaceTexture> retiredSource=new AtomicReference<>();
            TextureView retired=read(runner,()->{
                TextureView view=(TextureView)field(host,"texture");
                TextureView.SurfaceTextureListener delegate=view.getSurfaceTextureListener();
                retiredListener.set(delegate);retiredSource.set(view.getSurfaceTexture());
                view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
                    public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){delegate.onSurfaceTextureAvailable(s,w,h);}
                    public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){delegate.onSurfaceTextureSizeChanged(s,w,h);}
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture s){return delegate.onSurfaceTextureDestroyed(s);}
                    public void onSurfaceTextureUpdated(SurfaceTexture s){
                        if(!(boolean)field(host,"readingTexture") && expiredInDraw.compareAndSet(false,true))state(host).captureAt=android.os.SystemClock.elapsedRealtime()-1201;
                        delegate.onSurfaceTextureUpdated(s);
                    }
                });
                return view;
            });
            await(runner,()->expiredInDraw.get() && state(host).phase==SessionState.Phase.UNAVAILABLE
                && retired.getParent()==null,"deadline failure inside TextureView.draw safely detaches");
            read(runner,()->{
                assertReleased(host);
                assertNotPolling(host);
                if(host.findViewById(R.id.preview_cover).getVisibility()!=View.VISIBLE)throw new AssertionError("Deadline did not cover the old frame");
                return true;
            });
            read(runner,()->host.findViewById(R.id.restart_preview).performClick());
            await(runner,()->live(host),"manual restart after deadline in draw");
            read(runner,()->{
                long displayAt=state(host).displayAt;
                Object camera=field(host,"camera"),brightness=field(host,"brightnessReadback");
                // Ensure an unguarded old update would visibly change the timestamp.
                android.os.SystemClock.sleep(2);
                retiredListener.get().onSurfaceTextureUpdated(retiredSource.get());
                retiredListener.get().onSurfaceTextureDestroyed(retiredSource.get());
                if(!live(host) || state(host).displayAt!=displayAt || field(host,"camera")!=camera
                    || field(host,"brightnessReadback")!=brightness)
                    throw new AssertionError("Retired TextureView callbacks affected a new observation");
                return true;
            });
            report.append("RETIRED_TEXTURE_CALLBACK_PASS update_ignored=true destroy_ignored=true new_observation_retained=true\n");
            report.append("PASS deadline inside real TextureView.draw covers/stops immediately, defers layer detach, and supports manual restart\n");
            checkpoint(runner,"texture_deadline_recovered");

            // Real Home transition without PiP must release resources and stay stopped on return.
            shell(runner,"input keyevent KEYCODE_HOME");
            await(runner,()->state(host).phase==SessionState.Phase.UNAVAILABLE && !(boolean)field(host,"resumed")
                && !((Handler)field(host,"main")).hasCallbacks((Runnable)field(host,"watchdog")),"Home stops full preview");
            read(runner,()->{assertReleased(host);assertNotPolling(host);if(host.isInPictureInPictureMode())throw new AssertionError("Home auto-entered PiP");return true;});
            bringForward(runner,host);
            await(runner,()->(boolean)field(host,"resumed"),"return to stopped full preview");
            read(runner,()->{assertReleased(host);assertNotPolling(host);if(state(host).phase==SessionState.Phase.LIVE)throw new AssertionError("Return auto-started camera");return true;});
            read(runner,()->{if(host.findViewById(R.id.enable_depth).isEnabled())throw new AssertionError("Stopped preview allows mode switching");return true;});
            read(runner,()->host.findViewById(R.id.restart_preview).performClick());
            await(runner,()->live(host),"manual restart after Home");
            read(runner,()->{assertWatching(host);return true;});
            report.append("PASS Home releases camera; returned stopped preview does not poll; returning requires manual restart\n");
            checkpoint(runner,"home_restart_live");
            if(profilePriority==null && profileThreads==null) {
                PreviewReadinessProbe.run(runner,host,report);
                BrightnessActivityProbe.run(runner,host,report);
                ViewfinderProbe.run(runner,launcher,host,report);
            }

            read(runner,()->{
                host.findViewById(R.id.preview_recognition).performClick();
                if(!((CheckBox)host.findViewById(R.id.preview_recognition)).getText().toString().equals(host.getString(R.string.check_recognition)))
                    throw new AssertionError("Detection changed the concise toggle label");
                if((boolean)field(host,"analysisReady") || !((android.widget.TextView)host.findViewById(R.id.preview_info)).getText().toString().contains("准备识别器"))
                    throw new AssertionError("Cold detection did not show preparation state");
                return true;
            });
            checkpoint(runner,"recognition_on");
            await(runner,()->live(host) && basicAt(host)>0,"first actual model result");
            long firstResult=read(runner,()->basicAt(host));
            await(runner,()->live(host) && basicAt(host)>firstResult+400,"repeated actual model results");
            read(runner,()->{
                if(!((android.widget.TextView)host.findViewById(R.id.preview_title)).getText().toString().equals(host.getString(R.string.window_live))
                    || host.findViewById(R.id.detection_status).getVisibility()!=View.GONE)
                    throw new AssertionError("Detection-on retained a preview-only presentation");return true;
            });
            checkpoint(runner,"recognition_results");
            read(runner,()->{checkedNode((CheckBox)host.findViewById(R.id.preview_recognition));checkedNode((CheckBox)host.findViewById(R.id.preview_sound));return true;});
            report.append("PASS attached checkbox labels, checked and enabled states before/during actual detection\n");
            hapticActivity(runner,host,report);
            if(profilePriority==null && profileThreads==null)ToneProbe.run(runner,host,report);
            InferenceWorker.Ticket oldTicket=read(runner,()->(InferenceWorker.Ticket)field(host,"analysisTicket"));
            read(runner,()->{
                host.findViewById(R.id.preview_recognition).performClick();
                if(!oldTicket.cancelled || field(host,"analysisTicket")!=null || basicAt(host)!=-1)
                    throw new AssertionError("Detection off retained analysis or feedback");
                if((boolean)field(host,"analysisReady") || (boolean)field(host,"analysisHasResult"))throw new AssertionError("Detection off retained readiness");
                assertDefaultPreviewOnly(host,false);
                return true;
            });
            checkpoint(runner,"recognition_off");
            analysisRecovery(runner,host,launcher,report);
            read(runner,()->host.findViewById(R.id.preview_recognition).performClick());
            await(runner,()->live(host) && basicAt(host)>firstResult && field(host,"analysisTicket")!=oldTicket,"model restart after toggle");
            checkpoint(runner,"recognition_reenabled");
            report.append("PASS actual bundled-model results advance; off cancels Ticket/feedback; re-enable produces new results\n");
            InferenceWorker.Ticket geometryTicket=read(runner,()->(InferenceWorker.Ticket)field(host,"analysisTicket"));
            if(profilePriority==null && profileThreads==null)ViewfinderProbe.detection(runner,host,report);
            Object geometryModel=read(runner,()->field(field(host,"worker"),"detector"));
            if(geometryModel==null)throw new AssertionError("Actual model was not retained after a valid result");

            read(runner,()->host.findViewById(R.id.enter_pip).performClick());
            await(runner,host::isInPictureInPictureMode,"system PiP entry");
            read(runner,()->{assertWatching(host);return true;});
            checkpoint(runner,"pip_entered");
            await(runner,()->host.getSharedPreferences(HomeJourney.PREFERENCES,0).getBoolean(HomeJourney.PIP_SEEN,false),"PiP history");
            long first=read(runner,()->state(host).captureAt);
            await(runner,()->state(host).phase==SessionState.Phase.LIVE && state(host).captureAt>first+500
                && state(host).displayAt>first+500 && basicAt(host)>first+500,"capture, display and actual analysis advance in PiP");
            await(runner,()->publishedPipBoundsMatch(host),"source hint matches actual PiP camera pixels");
            read(runner,()->{
                for(int id:new int[]{R.id.preview_header,R.id.preview_controls,R.id.preview_actions})
                    if(host.findViewById(id).getVisibility()!=View.GONE)throw new AssertionError("Full controls leaked into PiP");
                return true;
            });
            screenshot(runner,"pip-camera.png");
            read(runner,()->{assertModelRetained(host,geometryTicket,geometryModel);return true;});
            report.append("PASS actual system PiP, advancing capture/display/model results, full controls hidden\n");
            checkpoint(runner,"pip_analysis_live");
            PipStopProbe.Evidence stopEvidence=new PipStopProbe.Evidence();
            RemoteAction firstVisitStop=PipStopProbe.capture(runner,host);
            await(runner,()->launcher.findViewById(R.id.stop).getVisibility()==View.VISIBLE
                && ((android.widget.TextView)launcher.findViewById(R.id.start)).getText().toString().equals(launcher.getString(R.string.home_return_preview)),"home offers fixed return and stop actions");
            read(runner,()->{
                if(launcher.findViewById(R.id.home_steps).getVisibility()!=View.GONE
                    || launcher.findViewById(R.id.home_status_container).getVisibility()!=View.VISIBLE)
                    throw new AssertionError("Completed onboarding displaced the actual observation status");
                return true;
            });

            // Expand the existing PiP Activity, the same route used by Android's CTS PiP app.
            read(runner,()->launcher.findViewById(R.id.start).performClick());
            await(runner,()->!host.isInPictureInPictureMode() && (boolean)field(host,"resumed"),"PiP expansion");
            await(runner,()->live(host) && host.findViewById(R.id.preview_controls).getVisibility()==View.VISIBLE,
                "full preview controls restored");
            long expandedAt=android.os.SystemClock.elapsedRealtime();
            await(runner,()->live(host) && state(host).recognition && basicAt(host)>expandedAt,"actual analysis after expansion");
            await(runner,()->publishedPipBoundsMatch(host),"source hint follows expanded destination camera pixels");
            read(runner,()->{assertModelRetained(host,geometryTicket,geometryModel);return true;});
            report.append("PIP_SOURCE_TRANSITION_PASS cases=2; actual PiP and expanded destination match the current texture Matrix\n");
            report.append("PASS expansion restores controls and keeps preview/model results LIVE\n");
            checkpoint(runner,"expanded_analysis_live");
            PipStopProbe.rejectFullscreen(runner,host,firstVisitStop,stopEvidence);

            RemoteAction previousObservationStop=pipDetectionControl(runner,host,launcher,report,firstVisitStop,stopEvidence);

            // Screen-off must stop even while the camera Activity was recently in PiP.
            shell(runner,"input keyevent KEYCODE_SLEEP");
            await(runner,()->state(host).phase==SessionState.Phase.UNAVAILABLE
                && !host.getSystemService(PowerManager.class).isInteractive() && !(boolean)field(host,"resumed")
                && !((Handler)field(host,"main")).hasCallbacks((Runnable)field(host,"watchdog")),"screen-off completes and stops camera");
            read(runner,()->{assertReleased(host);assertNotPolling(host);return true;});
            checkpoint(runner,"screen_off_complete");
            shell(runner,"input keyevent KEYCODE_WAKEUP");shell(runner,"wm dismiss-keyguard");
            await(runner,()->(boolean)field(host,"resumed") && host.getSystemService(PowerManager.class).isInteractive()
                && !host.getSystemService(KeyguardManager.class).isKeyguardLocked(),"wake, unlock and return to preview");
            awaitWakeInputReady(runner,host);
            checkpoint(runner,"wake_ready");
            read(runner,()->{assertReleased(host);return true;});
            read(runner,()->host.findViewById(R.id.restart_preview).performClick());
            await(runner,()->live(host),"manual restart after screen-off");
            read(runner,()->{
                if(state(host).recognition || ((CheckBox)host.findViewById(R.id.preview_recognition)).isChecked()
                    || ((CheckBox)host.findViewById(R.id.preview_sound)).isChecked() || field(host,"analysisTicket")!=null)
                    throw new AssertionError("New camera session did not default detection/sound off");
                return true;
            });
            report.append("PASS screen-off releases resources; wake/return does not restart camera\n");
            checkpoint(runner,"wake_restart_live");
            read(runner,()->{
                if(!host.getSystemService(PowerManager.class).isInteractive() || host.getSystemService(KeyguardManager.class).isKeyguardLocked())
                    throw new AssertionError("Replay requires an awake, unlocked device");
                long generation=state(host).generation;Object camera=field(host,"camera");
                ((BroadcastReceiver)field(host,"events")).onReceive(host,new Intent(Intent.ACTION_SCREEN_OFF));
                if(!live(host) || state(host).generation!=generation || field(host,"camera")!=camera)
                    throw new AssertionError("Late screen-off delivery stopped the explicitly restarted session");
                return true;
            });
            report.append("PASS replayed late screen-off delivery while awake/unlocked leaves the new session LIVE\n");
            checkpoint(runner,"late_screen_off_ignored");

            read(runner,()->host.findViewById(R.id.enter_pip).performClick());
            await(runner,host::isInPictureInPictureMode,"second PiP entry");
            RemoteAction currentStop=PipStopProbe.capture(runner,host);
            if(currentStop.getActionIntent().equals(previousObservationStop.getActionIntent()))throw new AssertionError("Restart reused the previous observation's stop PendingIntent");
            PipStopProbe.reject(runner,host,previousObservationStop,"previous_observation",stopEvidence);
            sendAction(runner,currentStop);
            await(runner,host::isDestroyed,"PiP stop action closes Activity");
            read(runner,()->{assertReleased(host);assertNotPolling(host);return true;});
            report.append("PASS PiP stop action releases resources and destroys Activity; early metadata waits=")
                .append(read(runner,()->field(host,"deferredCaptureCount"))).append('\n');
            await(runner,()->launcher.findViewById(R.id.stop).getVisibility()==View.GONE,"home removes inactive stop action");
            Instrumentation.ActivityMonitor reopened=runner.addMonitor(PreviewActivity.class.getName(),null,false);
            try {
                read(runner,()->launcher.findViewById(R.id.start).performClick());
                preview=(PreviewActivity)runner.waitForMonitorWithTimeout(reopened,7000);
            } finally {runner.removeMonitor(reopened);}
            if(preview==null || preview==host)throw new AssertionError("Home did not start a new preview after stop");
            PreviewActivity fresh=preview;
            await(runner,()->live(fresh),"new preview from fixed home action");
            read(runner,()->fresh.findViewById(R.id.enter_pip).performClick());
            await(runner,fresh::isInPictureInPictureMode,"home-stop PiP entry");
            PipStopProbe.reject(runner,fresh,currentStop,"previous_activity",stopEvidence);
            await(runner,()->launcher.findViewById(R.id.stop).getVisibility()==View.VISIBLE,"home stop available for new preview");
            read(runner,()->launcher.findViewById(R.id.stop).performClick());
            await(runner,fresh::isDestroyed,"home stop closes actual PiP camera");
            read(runner,()->{assertReleased(fresh);assertNotPolling(fresh);return true;});
            await(runner,()->launcher.findViewById(R.id.stop).getVisibility()==View.GONE,"home stop disappears immediately");
            report.append("PASS fixed home action expands the existing PiP, reopens after stop, and stops the new PiP session\n");

            // Keep the stopped-session checks independent of the existing stop-action sequence.
            Instrumentation.ActivityMonitor idleMonitor=runner.addMonitor(PreviewActivity.class.getName(),null,false);
            try {
                read(runner,()->launcher.findViewById(R.id.start).performClick());
                preview=(PreviewActivity)runner.waitForMonitorWithTimeout(idleMonitor,7000);
            } finally {runner.removeMonitor(idleMonitor);}
            if(preview==null || preview==fresh)throw new AssertionError("Idle-check preview did not open");
            PreviewActivity idleHost=preview;
            await(runner,()->live(idleHost),"idle-check actual camera LIVE");
            read(runner,()->idleHost.findViewById(R.id.enter_pip).performClick());
            await(runner,idleHost::isInPictureInPictureMode,"idle-check system PiP entry");
            HomeEventProbe.live(runner,(MainActivity)launcher,idleHost,report);
            // Simulate a dequeued watchdog expiring its own session, with home still visible.
            read(runner,()->{
                assertWatching(idleHost);
                Handler handler=(Handler)field(idleHost,"main");Runnable watchdog=(Runnable)field(idleHost,"watchdog");
                handler.removeCallbacks(watchdog);
                state(idleHost).captureAt=android.os.SystemClock.elapsedRealtime()-1201;
                watchdog.run();
                if(state(idleHost).phase!=SessionState.Phase.UNAVAILABLE)throw new AssertionError("Watchdog missed capture expiry");
                assertReleased(idleHost);assertNotPolling(idleHost);
                watchdog.run();assertNotPolling(idleHost);
                return true;
            });
            await(runner,()->PreviewActivity.hasPreview() && (boolean)field(launcher,"visible")
                && launcher.findViewById(R.id.stop).getVisibility()==View.GONE
                && ((TextView)launcher.findViewById(R.id.status)).getText().toString().equals(PreviewActivity.report())
                && !((Handler)field(launcher,"handler")).hasCallbacks((Runnable)field(launcher,"refresh")),"home stops polling retained stopped PiP");
            await(runner,()->RecoveryPresentationProbe.laidOut((View)field(idleHost,"root")),"stopped compact recovery layout");
            String stoppedReport=PreviewActivity.report();
            read(runner,()->{RecoveryPresentationProbe.compact((View)field(idleHost,"root"),
                RecoveryGuide.resolve((RecoveryGuide.Reason)field(idleHost,"recoveryReason"),true));return true;});
            // The stopped home views have updated; allow their queued draw to reach the screen capture.
            Thread.sleep(150);
            screenshot(runner,"pip-recovery-stopped.png");
            Thread.sleep(350);
            read(runner,()->{
                assertNotPolling(idleHost);
                if(((Handler)field(launcher,"handler")).hasCallbacks((Runnable)field(launcher,"refresh")))throw new AssertionError("Idle home resumed polling");
                return launcher.findViewById(R.id.start).performClick();
            });
            await(runner,()->!idleHost.isInPictureInPictureMode() && (boolean)field(idleHost,"resumed"),"expand stopped PiP without restart");
            await(runner,()->RecoveryPresentationProbe.laidOut((View)field(idleHost,"root"))
                && idleHost.findViewById(R.id.preview_controls).getVisibility()==View.VISIBLE,"expanded stopped recovery layout");
            read(runner,()->{
                RecoveryPresentationProbe.full((View)field(idleHost,"root"),
                    RecoveryGuide.resolve((RecoveryGuide.Reason)field(idleHost,"recoveryReason"),true));
                if(!stoppedReport.equals(PreviewActivity.report()))throw new AssertionError("Presentation change overwrote terminal home report: before="
                    +stoppedReport+" after="+PreviewActivity.report());
                return true;
            });
            screenshot(runner,"preview-recovery-expanded.png");
            read(runner,()->{assertReleased(idleHost);assertNotPolling(idleHost);return idleHost.findViewById(R.id.restart_preview).performClick();});
            await(runner,()->live(idleHost),"manual restart after watchdog expiry");
            read(runner,()->{RecoveryPresentationProbe.preview((View)field(idleHost,"root"));return true;});
            read(runner,()->{assertWatching(idleHost);return true;});
            long recoveredAt=android.os.SystemClock.elapsedRealtime();
            await(runner,()->live(idleHost) && state(idleHost).captureAt>recoveredAt+500
                && state(idleHost).displayAt>recoveredAt+500,"restarted camera keeps advancing with watchdog");
            report.append("PASS watchdog expiry and stale delivery leave visible stopped PiP/home idle; expansion stays stopped; manual restart re-arms protection\n");
            report.append("RECOVERY_PRESENTATION_ACTIVITY_PASS compact=2_lines full=reason_and_instructions settings=hidden manual_restart=restored no_auto_camera=true stopped_polling=false home_report_preserved=true\n");
            PipStopProbe.stopFailed(runner,idleHost,stopEvidence);
            PipStopProbe.finished(runner,stopEvidence,report);
            report.append("PIP_PROBE_PASS\n");
            result.putString("stream",report.toString());code=Activity.RESULT_OK;
        } catch(Throwable error) {
            failureEvidence(runner,"pip-failure",preview);
            result.putString("stream",report+"PIP_PROBE_FAIL: "+error+"; "+PreviewActivity.report());
        } finally {
            shell(runner,"input keyevent KEYCODE_WAKEUP");
            if(preview!=null){PreviewActivity host=preview;runner.runOnMainSync(host::finish);}
            if(home!=null){Activity host=home;runner.runOnMainSync(host::finish);}
        }
        if(profiledWorker!=null)profiledWorker.shutdown();
        runner.finish(code,result);
    }

    private static void bringForward(Instrumentation runner,PreviewActivity host) {
        // Explicit return after Home; waking an already foreground preview uses natural resume.
        read(runner,()->{host.startActivity(new Intent(host,PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));return true;});
    }

    /** Independent Android Matrix oracle; actual Activity rotation, no camera replacement. */
    private static void checkPipSourceBounds(Instrumentation runner,PreviewActivity host,StringBuilder report) throws Exception {
        int original=read(runner,host::getRequestedOrientation);
        boolean landscape=read(runner,()->host.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE);
        Object camera=read(runner,()->field(host,"camera")),texture=read(runner,()->field(host,"texture"));
        long generation=read(runner,()->state(host).generation);
        StringBuilder evidence=new StringBuilder();
        AtomicBoolean barsExcluded=new AtomicBoolean();
        try {
            for(int step=0;step<3;step++) {
                boolean wide=step==1?!landscape:landscape;
                if(step>0)read(runner,()->{host.setRequestedOrientation(wide
                    ?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);return true;});
                await(runner,()->{
                    boolean actualWide=host.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                    android.graphics.Rect expected=matrixSourceBounds(host);
                    android.app.PictureInPictureParams params=(android.app.PictureInPictureParams)field(host,"submittedPipParams");
                    return actualWide==wide && live(host) && expected!=null && params!=null
                        && nearby(expected,params.getSourceRectHint());
                },"current source hint after real orientation step="+step);
                long readyAt=android.os.SystemClock.elapsedRealtime();
                await(runner,()->live(host) && state(host).captureAt>readyAt+300 && state(host).displayAt>readyAt+300,
                    "camera advances after source bounds step="+step);
                int index=step;
                read(runner,()->{
                    if(state(host).generation!=generation || field(host,"camera")!=camera || field(host,"texture")!=texture)
                        throw new AssertionError("Source tracking replaced the active camera or TextureView");
                    TextureView view=(TextureView)texture;android.graphics.Rect legacy=new android.graphics.Rect();
                    if(!view.getGlobalVisibleRect(legacy))throw new AssertionError("Camera is not visible");
                    android.graphics.Rect expected=matrixSourceBounds(host);
                    android.app.PictureInPictureParams params=(android.app.PictureInPictureParams)field(host,"submittedPipParams");
                    android.graphics.Rect submitted=params.getSourceRectHint();
                    if(!nearby(expected,submitted) || !legacy.contains(submitted))throw new AssertionError("Source hint escaped the camera pixels");
                    if(legacy.width()-submitted.width()>2 || legacy.height()-submitted.height()>2)barsExcluded.set(true);
                    if(params.isAutoEnterEnabled())throw new AssertionError("Geometry tracking enabled automatic PiP");
                    double ratio=submitted.width()/(double)submitted.height();
                    if(Math.abs(ratio-params.getAspectRatio().doubleValue())>2d/submitted.height())
                        throw new AssertionError("Animation hint and PiP aspect ratio disagree");
                    evidence.append("SOURCE_BOUNDS step=").append(index).append(" landscape=").append(wide)
                        .append(" legacy=").append(legacy).append(" matrix=").append(expected).append(" submitted=").append(submitted)
                        .append(" retained_camera=true\n");return true;
                });
                screenshot(runner,step==1?"pip-source-rotated.png":"pip-source-portrait.png");
            }
            if(!barsExcluded.get())throw new AssertionError("The camera fixtures never exercised letterbox exclusion");
            SupportProbe.write(runner,"pip-source-bounds.txt",evidence.toString());
            report.append(evidence).append("PIP_SOURCE_BOUNDS_PASS cases=3; initial, real orientation change, restored; independent texture Matrix within 1px, no camera restart, frames advance\n");
        } finally {read(runner,()->{host.setRequestedOrientation(original);return true;});}
    }

    private static android.graphics.Rect matrixSourceBounds(PreviewActivity host) {
        TextureView texture=(TextureView)field(host,"texture");
        if(texture==null || texture.getWidth()==0 || texture.getHeight()==0)return null;
        android.graphics.Rect visible=new android.graphics.Rect();android.graphics.Point origin=new android.graphics.Point();
        if(!texture.getGlobalVisibleRect(visible,origin))return null;
        android.graphics.RectF image=new android.graphics.RectF(0,0,texture.getWidth(),texture.getHeight());
        texture.getTransform(new android.graphics.Matrix()).mapRect(image);
        android.graphics.Rect expected=new android.graphics.Rect((int)Math.floor(image.left)+origin.x,(int)Math.floor(image.top)+origin.y,
            (int)Math.ceil(image.right)+origin.x,(int)Math.ceil(image.bottom)+origin.y);
        return expected.intersect(visible)?expected:null;
    }

    private static boolean nearby(android.graphics.Rect a,android.graphics.Rect b) {
        return a!=null && b!=null && Math.abs(a.left-b.left)<=1 && Math.abs(a.top-b.top)<=1
            && Math.abs(a.right-b.right)<=1 && Math.abs(a.bottom-b.bottom)<=1;
    }

    private static boolean publishedPipBoundsMatch(PreviewActivity host) {
        android.app.PictureInPictureParams params=(android.app.PictureInPictureParams)field(host,"submittedPipParams");
        return params!=null && nearby(matrixSourceBounds(host),params.getSourceRectHint());
    }
    private static void awaitWakeInputReady(Instrumentation runner,PreviewActivity host) throws Exception {
        awaitPreviewInputReady(runner,host,"wake");
    }
    static void awaitPreviewInputReady(Instrumentation runner,PreviewActivity host,String reason) throws Exception {
        awaitPreviewInputReady(runner,host,reason,Long.MAX_VALUE);
    }
    private static void awaitPreviewInputReady(Instrumentation runner,PreviewActivity host,String reason,long deadline) throws Exception {
        // Wake/PiP transitions can resume/pause the Activity while input windows are changing.
        // A delivered or ambiguous gesture is never retried. A proved rejected DOWN
        // may reobserve readiness within the original tap budget before a second DOWN.
        long idleBudget=Math.min(5000,remainingMs(deadline));
        if(idleBudget<500)throw new AssertionError("Input readiness budget exhausted: "+reason);
        runner.getUiAutomation().waitForIdle(500,idleBudget);
        awaitUntil(runner,()->(boolean)field(host,"resumed") && host.hasWindowFocus()
            && host.getSystemService(PowerManager.class).isInteractive()
            && !host.getSystemService(KeyguardManager.class).isKeyguardLocked(),reason+" input window ready after event idle",Math.min(deadline,System.nanoTime()+10_000_000_000L));
        awaitSystemInputWindow(runner,host,deadline);
        awaitUntil(runner,()->(boolean)field(host,"resumed") && host.hasWindowFocus()
            && host.getSystemService(PowerManager.class).isInteractive()
            && !host.getSystemService(KeyguardManager.class).isKeyguardLocked(),reason+" state after input window readiness",Math.min(deadline,System.nanoTime()+10_000_000_000L));
        AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
        try {
            if(root==null || !android.text.TextUtils.equals(host.getPackageName(),root.getPackageName()))
                throw new AssertionError(reason+" active accessibility window is not the target application");
        } finally {if(root!=null)root.recycle();}
        android.util.Log.i("WalkBuddyInput",reason+"-ready after event idle and system input viewport/window; resumed/focus/unlocked/active-package checked");
    }
    private static void awaitSystemInputWindow(Instrumentation runner,Activity host) throws Exception {
        awaitSystemInputWindow(runner,host,Long.MAX_VALUE);
    }
    private static void awaitSystemInputWindow(Instrumentation runner,Activity host,long inputDeadline) throws Exception {
        String component=host.getComponentName().flattenToString();
        int displayId=read(runner,()->host.getWindow().getDecorView().getDisplay().getDisplayId());
        long budget=Math.min(5000,remainingMs(inputDeadline));
        if(budget<=0)throw new AssertionError("Input window readiness budget exhausted: "+component);
        long start=android.os.SystemClock.uptimeMillis(),deadline=start+budget;
        String snapshot="";
        do {
            snapshot=shellText(runner,"dumpsys -t 1 input");
            if(InputWindowState.touchable(snapshot,component,displayId,android.os.Process.myUid())) {
                powerEvidence(runner,"wake-input-ready-"+start+".txt",snapshot);
                android.util.Log.i("WalkBuddyInput","system input ready component="+component+" display="+displayId
                    +" waitedMs="+(android.os.SystemClock.uptimeMillis()-start));
                return;
            }
            Thread.sleep(50);
        } while(android.os.SystemClock.uptimeMillis()<deadline);
        powerEvidence(runner,"wake-input-not-ready-"+start+".txt",snapshot);
        throw new AssertionError("Timed out waiting for active input viewport and touchable target: "+component+" display="+displayId);
    }
    private static void screenIdleLifecycle(Instrumentation runner,PreviewActivity host,Activity launcher,StringBuilder report) throws Exception {
        read(runner,()->{assertDefaultPreviewOnly(host,false);return true;});
        String originalTimeout=shellText(runner,"settings get system screen_off_timeout").trim();
        String originalStayOn=shellText(runner,"settings get global stay_on_while_plugged_in").trim();
        if(!originalTimeout.matches("null|[0-9]+") || !originalStayOn.matches("null|[0-9]+"))
            throw new AssertionError("Cannot safely restore emulator screen settings");
        long effective;
        try {
            shell(runner,"settings put global stay_on_while_plugged_in 0");
            shell(runner,"settings put system screen_off_timeout 1000");
            String power=shellText(runner,"dumpsys -t 3 power");
            long appliedDeadline=android.os.SystemClock.elapsedRealtime()+5000;
            while(!shortIdleSettingsApplied(power) && android.os.SystemClock.elapsedRealtime()<appliedDeadline) {
                Thread.sleep(50);power=shellText(runner,"dumpsys -t 3 power");
            }
            java.util.regex.Matcher minimum=java.util.regex.Pattern.compile("(?m)^\\s*mMinimumScreenOffTimeoutConfig=(\\d+)\\s*$").matcher(power);
            if(!minimum.find() || !shortIdleSettingsApplied(power))throw new AssertionError("Short idle timeout was not applied");
            effective=Math.max(1000,Long.parseLong(minimum.group(1)));
            if(effective>15000)throw new AssertionError("Unexpected emulator minimum screen timeout: "+effective);
            powerEvidence(runner,"screen-idle-config.txt",power);
            await(runner,()->host.hasWindowFocus() && keepsScreenOn(host),"full preview owns screen hold");
            long until=android.os.SystemClock.elapsedRealtime()+effective+1500;
            while(android.os.SystemClock.elapsedRealtime()<until) {
                read(runner,()->{
                    if(!live(host) || !keepsScreenOn(host) || !host.getSystemService(PowerManager.class).isInteractive())
                        throw new AssertionError("Idle full preview lost live camera or allowed screen off");
                    return true;
                });
                Thread.sleep(200);
            }
            report.append("SCREEN_IDLE_FULL_PASS requestedMs=1000 effectiveMinimumMs=").append(effective)
                .append(" observedWithoutInputMs=").append(effective+1500).append('\n');
            powerEvidence(runner,"screen-idle-full-power.txt",shellText(runner,"dumpsys -t 3 power"));
            int fullWidth=read(runner,()->((View)field(host,"root")).getWidth());
            int fullHeight=read(runner,()->((View)field(host,"root")).getHeight());
            tap(runner,host,R.id.enter_pip);
            await(runner,()->host.isInPictureInPictureMode() && !keepsScreenOn(host),"PiP releases screen hold");
            await(runner,()->warningReady(host) && ((View)field(host,"root")).getWidth()<fullWidth
                && ((View)field(host,"root")).getHeight()<fullHeight && publishedPipBoundsMatch(host),
                "default preview-only PiP actually shrinks");
            long settledAt=android.os.SystemClock.elapsedRealtime();
            await(runner,()->live(host) && state(host).captureAt>settledAt+150 && state(host).displayAt>settledAt+150,
                "new frames in settled default preview-only PiP");
            read(runner,()->{assertDefaultPreviewOnly(host,true);return true;});
            String dimensions=read(runner,()->"PREVIEW_ONLY_GEOMETRY_PASS full="+fullWidth+"x"+fullHeight
                +" compact="+((View)field(host,"root")).getWidth()+"x"+((View)field(host,"root")).getHeight()
                +" frames_advance=true default_detection=false sound=false\n");
            report.append(dimensions);SupportProbe.write(runner,"default-preview-only-size.txt",dimensions);
            screenshot(runner,"pip-default-preview-only.png");
            long deadline=android.os.SystemClock.elapsedRealtime()+effective+8000;
            while(read(runner,()->host.getSystemService(PowerManager.class).isInteractive())
                && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
            if(read(runner,()->host.getSystemService(PowerManager.class).isInteractive()))
                throw new AssertionError("PiP prevented configured automatic screen off");
            await(runner,()->state(host).phase==SessionState.Phase.UNAVAILABLE,"automatic screen off stops camera");
            read(runner,()->{assertReleased(host);assertNotPolling(host);return true;});
            powerEvidence(runner,"screen-idle-pip-power.txt",shellText(runner,"dumpsys -t 3 power"));
            report.append("SCREEN_IDLE_PIP_PASS automatic screen off releases camera and polling\n");
        } finally {
            // Preserve the failing power state before restoring fixture settings or waking up.
            try{powerEvidence(runner,"screen-idle-final-power.txt",shellText(runner,"dumpsys -t 3 power"));}
            catch(Throwable ignored){/* Keep the original failure and always restore settings. */}
            shell(runner,"settings "+("null".equals(originalTimeout)?"delete system screen_off_timeout":"put system screen_off_timeout "+originalTimeout));
            shell(runner,"settings "+("null".equals(originalStayOn)?"delete global stay_on_while_plugged_in":"put global stay_on_while_plugged_in "+originalStayOn));
            shell(runner,"input keyevent KEYCODE_WAKEUP");shell(runner,"wm dismiss-keyguard");
        }
        runner.getUiAutomation().waitForIdle(500,5000);
        await(runner,()->(boolean)field(launcher,"visible") && launcher.hasWindowFocus(),"home restored after PiP automatic screen off");
        awaitSystemInputWindow(runner,launcher);
        read(runner,()->{assertReleased(host);return true;});
        tap(runner,launcher,R.id.start);
        await(runner,()->!host.isInPictureInPictureMode() && (boolean)field(host,"resumed") && host.hasWindowFocus()
            && host.getSystemService(PowerManager.class).isInteractive()
            && !host.getSystemService(KeyguardManager.class).isKeyguardLocked(),"return after automatic screen off");
        awaitWakeInputReady(runner,host);
        read(runner,()->{assertReleased(host);return true;});
        tap(runner,host,R.id.restart_preview);
        await(runner,()->live(host) && keepsScreenOn(host),"manual restart restores full preview screen hold");
        read(runner,()->{assertDefaultPreviewOnly(host,false);return true;});
        report.append("PREVIEW_ONLY_ACTIVITY_PASS cases=3; default full, first actual PiP, explicit restart; actual virtual camera, no automatic detection or sound\n");
        report.append("PASS screen hold only for active full preview; real idle timeout keeps full preview awake, PiP sleeps, wake stays stopped, manual restart restores hold\n");
    }
    private static boolean keepsScreenOn(Activity host) {
        return (host.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;
    }
    private static boolean shortIdleSettingsApplied(String power) {
        return java.util.regex.Pattern.compile("(?m)^\\s*mScreenOffTimeoutSetting=1000\\s*$").matcher(power).find()
            && java.util.regex.Pattern.compile("(?m)^\\s*mStayOnWhilePluggedInSetting=0\\s*$").matcher(power).find();
    }
    private static void powerEvidence(Instrumentation runner,String name,String text) throws Exception {
        File directory=new File(runner.getTargetContext().getCacheDir(),"ui-probe");
        if(!directory.isDirectory() && !directory.mkdirs())throw new AssertionError("Power evidence directory unavailable");
        try(FileOutputStream output=new FileOutputStream(new File(directory,name))) {
            output.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private static RemoteAction pipDetectionControl(Instrumentation runner,PreviewActivity host,Activity launcher,StringBuilder report,
        RemoteAction previousVisitStop,PipStopProbe.Evidence stopEvidence) throws Exception {
        Object camera=read(runner,()->field(host,"camera")),texture=read(runner,()->field(host,"texture"));
        InferenceWorker.Ticket ticket=read(runner,()->(InferenceWorker.Ticket)field(host,"analysisTicket"));
        if(ticket==null || !read(runner,()->state(host).recognition))throw new AssertionError("PiP control needs active detection");
        int fullWidth=read(runner,()->((View)field(host,"root")).getWidth());
        int fullHeight=read(runner,()->((View)field(host,"root")).getHeight());
        tap(runner,host,R.id.enter_pip);
        await(runner,()->host.isInPictureInPictureMode() && ((View)field(host,"root")).getWidth()<fullWidth
            && ((View)field(host,"root")).getHeight()<fullHeight,"detection control in settled PiP");
        PipStopProbe.reject(runner,host,previousVisitStop,"earlier_pip_visit",stopEvidence);
        PipStopProbe.rejectLegacy(runner,host,stopEvidence);
        PipStopProbe.rejectOtherOwner(runner,host,stopEvidence);
        RemoteAction stop=PipStopProbe.capture(runner,host);
        PipSoundProbe.run(runner,host,launcher,report);
        RemoteAction action=read(runner,()->{
            java.util.List<RemoteAction> actions=((android.app.PictureInPictureParams)field(host,"submittedPipParams")).getActions();
            if(actions.size()!=Math.min(3,host.getMaxNumPictureInPictureActions()) || !actions.get(0).getTitle().toString().equals(host.getString(R.string.preview_stop)))
                throw new AssertionError("Active PiP must retain stop and expose detection control");
            RemoteAction off=actions.get(1);
            if(!off.getTitle().toString().equals(host.getString(R.string.pip_stop_detection)) || !off.isEnabled()
                || !off.getContentDescription().toString().equals(host.getString(R.string.pip_stop_detection_description)))
                throw new AssertionError("PiP detection control has incorrect label or semantics");
            StopPreviewReceiver receiver=new StopPreviewReceiver();
            receiver.onReceive(host,new Intent(StopPreviewReceiver.STOP_DETECTION)
                .putExtra(StopPreviewReceiver.OWNER,"retired-owner").putExtra(StopPreviewReceiver.GENERATION,state(host).generation));
            receiver.onReceive(host,new Intent(StopPreviewReceiver.STOP_DETECTION)
                .putExtra(StopPreviewReceiver.OWNER,(String)field(host,"pipOwner"))
                .putExtra(StopPreviewReceiver.GENERATION,state(host).generation-1));
            receiver.onReceive(host,new Intent("unrecognized-action"));
            if(!state(host).recognition || field(host,"analysisTicket")!=ticket || ticket.cancelled)
                throw new AssertionError("Retired or unrecognized PiP action changed active detection");
            ((CheckBox)host.findViewById(R.id.preview_sound)).setChecked(true);
            setField(host,"pendingSpeak",true);
            return off;
        });
        sendAction(runner,action);
        await(runner,()->!state(host).recognition && (int)field(host,"detectionNotice")==R.string.info_detection_paused,
            "PiP PendingIntent stops detection");
        read(runner,()->{
            assertPreviewOnly(host,camera,texture);
            if(!ticket.cancelled || ((android.app.PictureInPictureParams)field(host,"submittedPipParams")).getActions().size()!=1)
                throw new AssertionError("PiP off retained Ticket or obsolete menu action");
            return true;
        });
        long stoppedAt=android.os.SystemClock.elapsedRealtime();
        await(runner,()->live(host) && state(host).captureAt>stoppedAt+300 && state(host).displayAt>stoppedAt+300,
            "PiP camera keeps advancing after manual detection off");
        await(runner,()->warningReady(host),"manual PiP preview-only status laid out");
        read(runner,()->{assertWarningFits(host);return true;});
        screenshot(runner,"pip-detection-off.png");
        sendAction(runner,action);
        read(runner,()->{assertPreviewOnly(host,camera,texture);return true;});
        tap(runner,launcher,R.id.start);
        await(runner,()->!host.isInPictureInPictureMode() && (boolean)field(host,"resumed"),"expand after PiP detection off");
        read(runner,()->{assertPreviewOnly(host,camera,texture);return true;});
        screenshot(runner,"preview-after-pip-detection-off.png");
        tap(runner,host,R.id.preview_recognition);
        await(runner,()->live(host) && state(host).recognition && basicAt(host)>stoppedAt,"manual model restart after PiP detection off");
        InferenceWorker.Ticket resumedTicket=read(runner,()->(InferenceWorker.Ticket)field(host,"analysisTicket"));
        sendAction(runner,action);
        read(runner,()->{
            if(!state(host).recognition || field(host,"analysisTicket")!=resumedTicket || resumedTicket.cancelled
                || ((CheckBox)host.findViewById(R.id.preview_sound)).isChecked()
                || host.findViewById(R.id.detection_status).getVisibility()!=View.GONE)
                throw new AssertionError("Replayed PiP action affected full preview or manual restart restored sound");
            return true;
        });
        report.append("PASS actual PiP menu PendingIntent stops detection/sound/feedback while camera/texture advance; menu removes detection action; repeat is idempotent; owner/generation/fullscreen guards; expansion stays off; manual model restart keeps sound off\n");
        return stop;
    }
    static void sendAction(Instrumentation runner,RemoteAction action) throws Exception {
        java.util.concurrent.CountDownLatch delivered=new java.util.concurrent.CountDownLatch(1);
        action.getActionIntent().send(runner.getTargetContext(),0,null,
            (pending,intent,code,data,extras)->delivered.countDown(),new Handler(android.os.Looper.getMainLooper()));
        if(!delivered.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("PiP action delivery did not finish");
    }
    private static void analysisRecovery(Instrumentation runner,PreviewActivity host,Activity launcher,StringBuilder report) throws Exception {
        @SuppressWarnings("unchecked") InferenceWorker<BitmapFrame,LocalDetector.Result> original=
            (InferenceWorker<BitmapFrame,LocalDetector.Result>)field(host,"worker");
        if(!original.awaitIdle(3000))throw new AssertionError("Original worker did not close after detection off");
        Object camera=read(runner,()->field(host,"camera")),texture=read(runner,()->field(host,"texture"));
        InferenceWorker<BitmapFrame,LocalDetector.Result> broken=new InferenceWorker<>(android.os.SystemClock::elapsedRealtime,
            ()->{throw new java.io.IOException("Injected model load failure");});
        try {
            read(runner,()->{setField(host,"worker",broken);host.findViewById(R.id.preview_recognition).performClick();return true;});
            await(runner,()->(int)field(host,"detectionNotice")==R.string.info_detection_failed,"model failure becomes preview-only");
            String failedReport=read(runner,()->{assertPreviewOnly(host,camera,texture);return detectionProblem(host,"DETECTION_ERROR");});
            SupportProbe.write(runner,"diagnostics-model-error.txt",failedReport);
            await(runner,()->warningReady(host),"model failure warning laid out");
            read(runner,()->{assertWarningFits(host);return true;});
        } finally {broken.shutdown();if(!broken.awaitIdle(3000))throw new AssertionError("Broken worker did not close");}
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<BitmapFrame> retained=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger closed=new java.util.concurrent.atomic.AtomicInteger();
        InferenceWorker<BitmapFrame,LocalDetector.Result> blocked=new InferenceWorker<>(android.os.SystemClock::elapsedRealtime,
            ()->new InferenceWorker.Detector<BitmapFrame,LocalDetector.Result>() {
                public LocalDetector.Result detect(BitmapFrame frame) throws Exception {
                    retained.set(frame);entered.countDown();release.await();
                    return new LocalDetector.Result("人",new int[0],new float[0],java.util.Collections.emptyList());
                }
                public void close(){closed.incrementAndGet();}
            });
        try {
            read(runner,()->{setField(host,"worker",blocked);host.findViewById(R.id.preview_recognition).performClick();return true;});
            if(!entered.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Injected native work did not start");
            read(runner,()->{
                state(host).analysisAt=android.os.SystemClock.elapsedRealtime()-1501;
                Runnable watchdog=(Runnable)field(host,"watchdog");
                ((Handler)field(host,"main")).removeCallbacks(watchdog);watchdog.run();
                assertPreviewOnly(host,camera,texture);
                if(retained.get().bitmap.isRecycled())throw new AssertionError("Cancelled active frame was recycled during native work");
                return true;
            });
            SupportProbe.write(runner,"diagnostics-model-timeout.txt",read(runner,()->detectionProblem(host,"DETECTION_TIMEOUT")));
            long stoppedAt=android.os.SystemClock.elapsedRealtime();
            await(runner,()->live(host) && state(host).captureAt>stoppedAt+300 && state(host).displayAt>stoppedAt+300,
                "camera advances after analysis timeout");
            await(runner,()->warningReady(host),"timeout warning laid out");
            read(runner,()->{assertWarningFits(host);return true;});
            screenshot(runner,"detection-timeout-preview.png");
            int fullWidth=read(runner,()->((View)field(host,"root")).getWidth());
            int fullHeight=read(runner,()->((View)field(host,"root")).getHeight());
            tap(runner,host,R.id.enter_pip);
            await(runner,host::isInPictureInPictureMode,"preview-only PiP after analysis timeout");
            await(runner,()->((View)field(host,"root")).getWidth()<fullWidth
                && ((View)field(host,"root")).getHeight()<fullHeight,"preview-only PiP window actually shrinks");
            long pipAt=android.os.SystemClock.elapsedRealtime();
            await(runner,()->live(host) && state(host).captureAt>pipAt+300 && state(host).displayAt>pipAt+300,
                "preview-only PiP frames advance during blocked analysis");
            read(runner,()->{assertPreviewOnly(host,camera,texture);return true;});
            await(runner,()->warningReady(host),"PiP timeout warning laid out");
            read(runner,()->{assertWarningFits(host);return true;});
            screenshot(runner,"detection-timeout-pip.png");
            release.countDown();
            if(!blocked.awaitIdle(3000) || closed.get()!=1 || !retained.get().bitmap.isRecycled())
                throw new AssertionError("Cancelled native result did not release its frame/model exactly once");
            read(runner,()->{assertPreviewOnly(host,camera,texture);return true;});
            tap(runner,launcher,R.id.start);
            await(runner,()->!host.isInPictureInPictureMode() && (boolean)field(host,"resumed"),"return from preview-only PiP");
            read(runner,()->{assertPreviewOnly(host,camera,texture);return true;});
            report.append("PASS injected model load failure and blocked analysis keep camera/texture; detection/sound/feedback stop; native frame retained until return; late result ignored; preview-only warning persists in PiP/expansion\n");
        } finally {
            release.countDown();blocked.shutdown();blocked.awaitIdle(3000);
            read(runner,()->{setField(host,"worker",original);return true;});
        }
    }
    private static void hapticActivity(Instrumentation runner,PreviewActivity host,StringBuilder report) {
        read(runner,()->{
            RiskHaptics original=(RiskHaptics)field(host,"haptics");original.clear();
            HapticsProbe.Output output=new HapticsProbe.Output();RiskHaptics recorded=new RiskHaptics(output);
            DetectionFeedback feedback=(DetectionFeedback)field(host,"feedback");
            CheckBox sound=host.findViewById(R.id.preview_sound);
            CheckBox vibration=host.findViewById(R.id.preview_vibration);
            if(!sound.isEnabled() || sound.isChecked())throw new AssertionError("Haptic check requires active detection and sound off");
            if(!vibration.isEnabled() || !vibration.isChecked())throw new AssertionError("Haptic check requires the default vibration choice");
            setField(host,"haptics",recorded);
            try {
                long now=android.os.SystemClock.elapsedRealtime();feedback.clear();
                Detection near=new Detection("构造触觉检查",1,.3f,.1f,.7f,.9f,Detection.Risk.NEAR,.7f,null);
                for(long at:new long[]{now-3,now-2})feedback.depth(new DepthSnapshot(at,java.util.Collections.singletonList(near),
                    "构造触觉检查",DepthSnapshot.Stage.MEASURING,true,true),0,at);
                Method alert=PreviewActivity.class.getDeclaredMethod("alert");alert.setAccessible(true);alert.invoke(host);
                if(output.plays!=1 || output.last!=RiskHaptics.Cue.DOUBLE)throw new AssertionError("Activity did not select current near cue");
                sound.setChecked(true);sound.setChecked(false);
                if(output.cancels!=0)throw new AssertionError("Sound toggle cancelled tactile feedback");
                Method show=PreviewActivity.class.getDeclaredMethod("showFeedback",long.class);show.setAccessible(true);
                feedback.depth(new DepthSnapshot(now-1,java.util.Collections.singletonList(near.withRisk(Detection.Risk.CAUTION)),
                    "构造降级",DepthSnapshot.Stage.MEASURING,true,true),0,now-1);
                show.invoke(host,now);
                if(output.cancels!=1 || output.plays!=1)throw new AssertionError("Downgrade did not cancel remaining cue");
                recorded.alert(Detection.Risk.NEAR);
                feedback.clear();alert.invoke(host);
                if(output.cancels!=2 || output.plays!=2)throw new AssertionError("Empty current result replayed or retained a cue");
                recorded.alert(Detection.Risk.NEAR);
                Method silence=PreviewActivity.class.getDeclaredMethod("silence");silence.setAccessible(true);silence.invoke(host);
                if(output.cancels!=3 || field(recorded,"requested")!=Detection.Risk.CAPTURED)
                    throw new AssertionError("Stop did not clear the tactile request");
                report.append("HAPTIC_ACTIVITY_PASS cases=5; current near, Sound-off independence, downgrade, empty current, stop; recorded output, no physical motor\n");
                Object camera=field(host,"camera");long generation=state(host).generation;
                sound.setChecked(true);vibration.performClick();checkedNode(vibration);
                if(recorded.enabled() || output.cancels!=4 || !sound.isChecked() || !state(host).recognition
                    || field(host,"camera")!=camera || state(host).generation!=generation)
                    throw new AssertionError("Vibration mute did not cancel independently");
                if(!((android.widget.TextView)host.findViewById(R.id.legend_near)).getText().toString().contains("振动已关"))
                    throw new AssertionError("Activity legend disagrees with the vibration choice");
                long fresh=android.os.SystemClock.elapsedRealtime();feedback.clear();
                for(long at:new long[]{fresh-2,fresh-1})feedback.depth(new DepthSnapshot(at,java.util.Collections.singletonList(near),
                    "构造关闭振动",DepthSnapshot.Stage.MEASURING,true,true),0,at);
                sound.setChecked(false);
                alert.invoke(host);
                if(output.plays!=3)throw new AssertionError("Muted Activity still requested tactile feedback");
                vibration.performClick();checkedNode(vibration);
                if(!recorded.enabled() || output.plays!=3)throw new AssertionError("Enabling vibration replayed the old cue");
                alert.invoke(host);
                if(output.plays!=4 || output.last!=RiskHaptics.Cue.DOUBLE)throw new AssertionError("New explicit alert did not honor re-enabled vibration");
                report.append("HAPTIC_SETTINGS_ACTIVITY_PASS cases=3; mute cancels independently, muted alert silent, enable waits for new dispatch; constructed risks and recorded output\n");
            } catch(ReflectiveOperationException error){throw new AssertionError(error);}
            finally {
                sound.setChecked(false);
                vibration.setChecked(true);
                recorded.clear();feedback.clear();((RiskAnnouncer)field(host,"announcer")).clear();
                setField(host,"pendingSpeak",false);setField(host,"haptics",original);
            }
            return true;
        });
    }

    private static String detectionProblem(PreviewActivity host,String category) {
        SessionDiagnostics diagnostics=(SessionDiagnostics)field(host,"diagnostics");SessionDiagnostics.Snapshot snapshot=diagnostics.detection();
        if(snapshot==null || !category.equals(snapshot.category) || !snapshot.recognition
            || host.findViewById(R.id.copy_problem_info).getVisibility()!=View.VISIBLE)
            throw new AssertionError("Detection evidence was lost before state reset");
        if(category.equals("DETECTION_TIMEOUT") && snapshot.analysisAge<1501)throw new AssertionError("Original timeout age was lost");
        return diagnostics.report(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,Build.MANUFACTURER,Build.MODEL,
            Build.VERSION.RELEASE,Build.VERSION.SDK_INT);
    }

    private static void assertDefaultPreviewOnly(PreviewActivity host,boolean compact) {
        if(!live(host) || state(host).recognition || (int)field(host,"detectionNotice")!=0 || field(host,"analysisTicket")!=null
            || ((CheckBox)host.findViewById(R.id.preview_recognition)).isChecked() || field(host,"activeTone")!=null
            || ((CheckBox)host.findViewById(R.id.preview_sound)).isChecked() || host.findViewById(R.id.preview_sound).isEnabled()
            || host.isInPictureInPictureMode()!=compact)
            throw new AssertionError("Expected an unchanged default camera-only session");
        android.widget.TextView title=host.findViewById(R.id.preview_title),notice=host.findViewById(R.id.detection_status);
        if(!title.getText().toString().equals(host.getString(R.string.window_preview_only))
            || notice.getVisibility()!=(compact?View.VISIBLE:View.GONE))throw new AssertionError("Default detection-off status is misleading");
        if(compact) {
            assertWarningFits(host);
            if(!notice.getText().toString().equals(host.getString(R.string.detection_stopped_compact))
                || host.findViewById(R.id.pip_depth_status).getVisibility()!=View.GONE || host.findViewById(R.id.posture_hint).getVisibility()!=View.GONE)
                throw new AssertionError("Default preview-only PiP notice lost priority or meaning");
        }
    }
    private static void assertPreviewOnly(PreviewActivity host,Object camera,Object texture) {
        if(!live(host) || field(host,"camera")!=camera || field(host,"texture")!=texture || state(host).recognition
            || field(host,"analysisTicket")!=null || basicAt(host)!=-1 || field(host,"activeTone")!=null
            || (boolean)field(host,"pendingSpeak") || ((CheckBox)host.findViewById(R.id.preview_recognition)).isChecked()
            || ((CheckBox)host.findViewById(R.id.preview_sound)).isChecked() || host.findViewById(R.id.preview_sound).isEnabled()
            || ((Object[])field(field(host,"detectionOverlay"),"entries")).length!=0
            || host.findViewById(R.id.recent_alert).getVisibility()!=View.GONE)
            throw new AssertionError("Detection fault did not preserve only the healthy camera");
        if(host.findViewById(R.id.detection_status).getVisibility()!=View.VISIBLE)
            throw new AssertionError("Preview-only warning was not enabled");
        if(host.isInPictureInPictureMode() && (host.findViewById(R.id.pip_depth_status).getVisibility()!=View.GONE
            || host.findViewById(R.id.posture_hint).getVisibility()!=View.GONE))
            throw new AssertionError("Optional status obscured the preview-only PiP");
        assertWatching(host);
    }
    private static boolean warningReady(PreviewActivity host) {
        android.widget.TextView warning=host.findViewById(R.id.detection_status);
        return warning.isShown() && warning.getWidth()>0 && warning.getHeight()>0
            && warning.getLayout()!=null && !warning.isLayoutRequested();
    }
    private static void assertWarningFits(PreviewActivity host) {
        android.widget.TextView warning=host.findViewById(R.id.detection_status);
        android.graphics.Rect visible=new android.graphics.Rect();
        if(!warning.isShown() || !warning.getGlobalVisibleRect(visible) || warning.getLayout()==null || visible.height()<warning.getHeight())
            throw new AssertionError("Preview-only warning is not visible; "+touchState(host,R.id.detection_status)
                +" warningSize="+warning.getWidth()+"x"+warning.getHeight()+" rootSize="+((View)field(host,"root")).getWidth()+"x"+((View)field(host,"root")).getHeight());
        for(int line=0;line<warning.getLineCount();line++)if(warning.getLayout().getEllipsisCount(line)>0)
            throw new AssertionError("Preview-only warning was truncated");
    }
    private static void setField(Object host,String name,Object value) {
        try{Field field=host.getClass().getDeclaredField(name);field.setAccessible(true);field.set(host,value);}
        catch(Exception error){throw new AssertionError(error);}
    }
    static void tap(Instrumentation runner,Activity host,int id) throws Exception {
        // Real input respects focus and enabled state, unlike directly invoking performClick.
        long deadline=System.nanoTime()+10_000_000_000L;
        RestoreCheckpoint checkpoint=null;
        for(int attempt=1;attempt<=2;attempt++) {
            try {awaitUntil(runner,()->{
                View view=host.findViewById(id);android.graphics.Rect bounds=new android.graphics.Rect();
                if(view==null)return false;
                for(View node=view;node!=null;) {
                    if(node.isLayoutRequested() || !node.isLaidOut())return false;
                    android.view.ViewParent parent=node.getParent();node=parent instanceof View?(View)parent:null;
                }
                return host.hasWindowFocus() && view!=null && view.isShown() && view.isEnabled()
                    && view.isAttachedToWindow() && view.getGlobalVisibleRect(bounds) && !bounds.isEmpty();
            },"touch target ready id="+id,deadline);} catch(AssertionError failure) {
                throw new AssertionError(failure.getMessage()+"; "+read(runner,()->touchState(host,id)),failure);
            }
            if(attempt==1 && host instanceof PreviewActivity && id==R.id.restart_preview)checkpoint=read(runner,()->{
                PreviewActivity preview=(PreviewActivity)host;View view=host.findViewById(id);
                if(!inactive(preview) || view.isLongClickable())return null;
                return new RestoreCheckpoint(view,state(preview).startedAt,
                    ((SessionDiagnostics)field(preview,"diagnostics")).preview());
            });
            String before=checkpoint!=null?restoreInputRouting(runner):null;
            if(checkpoint!=null && remainingMs(deadline)<=0)throw new AssertionError("Restore input readiness budget exhausted before DOWN");
            android.graphics.Rect bounds=read(runner,()->{
                android.graphics.Rect rect=new android.graphics.Rect();host.findViewById(id).getGlobalVisibleRect(rect);return rect;
            });
            android.util.Log.i("WalkBuddyInput","tap id="+host.getResources().getResourceEntryName(id)+" bounds="+bounds
                +" focus="+host.hasWindowFocus()+" pip="+host.isInPictureInPictureMode()+" attempt="+attempt
                +" restoreGuard="+(checkpoint!=null));
            long at=android.os.SystemClock.uptimeMillis();
            sendTouch(runner,host,id,bounds,at,android.view.MotionEvent.ACTION_DOWN);
            boolean pressed=read(runner,()->{
                android.graphics.Rect observed=new android.graphics.Rect();View current=host.findViewById(id);
                if(current!=null)current.getGlobalVisibleRect(observed);
                boolean acknowledged=current!=null && current.isPressed();
                android.util.Log.i("WalkBuddyInput","after-down id="+host.getResources().getResourceEntryName(id)+" bounds="+observed
                    +" focus="+host.hasWindowFocus()+" pip="+host.isInPictureInPictureMode()+" pressed="+acknowledged);
                return acknowledged;
            });
            if(checkpoint!=null && !pressed) {
                String after=restoreInputRouting(runner);
                if(RejectedTouch.pausedDown(before,after,at,android.os.Process.myPid(),host.getComponentName().flattenToString())) {
                    powerEvidence(runner,"restore-rejected-down-"+android.os.Process.myPid()+"-"+at+".txt",
                        "BEFORE\n"+before+"\nAFTER\n"+after);
                    // No window received DOWN. CANCEL ends the rejected sequence without
                    // producing a click; never reissue a delivered or ambiguous gesture.
                    sendTouch(runner,host,id,bounds,at,android.view.MotionEvent.ACTION_CANCEL);
                    RestoreCheckpoint original=checkpoint;
                    boolean unchanged=read(runner,()->{
                        PreviewActivity preview=(PreviewActivity)host;
                        boolean ownerStable=!host.isDestroyed() && !host.isFinishing() && !host.isInPictureInPictureMode()
                            && host.findViewById(id)==original.view;
                        if(!RejectedTouch.sameInactiveObservation(inactive(preview),ownerStable,original.startedAt,
                            state(preview).startedAt,original.incident,
                            ((SessionDiagnostics)field(preview,"diagnostics")).preview()))return false;
                        assertReleased(preview);return true;
                    });
                    boolean retry=RejectedTouch.mayRetry(attempt,true,unchanged,remainingMs(deadline));
                    android.util.Log.i("WalkBuddyInput","restore DOWN rejected eventTimeMs="+at+" attempt="+attempt
                        +" paused_no_targets=true unchanged="+unchanged+" retry="+retry+" remainingMs="+remainingMs(deadline));
                    if(!retry)throw new AssertionError("Restore DOWN rejected with no input target; attempt="+attempt+" unchanged="+unchanged);
                    awaitPreviewInputReady(runner,(PreviewActivity)host,"after rejected restore DOWN",deadline);
                    continue;
                }
            }
            sendTouch(runner,host,id,bounds,at,android.view.MotionEvent.ACTION_UP);
            return;
        }
        throw new AssertionError("Restore input attempt bound exceeded");
    }
    private static boolean inactive(PreviewActivity host) {
        return state(host).phase==SessionState.Phase.UNAVAILABLE || state(host).phase==SessionState.Phase.STOPPED;
    }
    private static final class RestoreCheckpoint {
        final View view;final long startedAt;final Object incident;
        RestoreCheckpoint(View view,long startedAt,Object incident){this.view=view;this.startedAt=startedAt;this.incident=incident;}
    }
    private static String restoreInputRouting(Instrumentation runner) {
        return shellText(runner,"logcat -d -t 1000 -s InputDispatcher:I InputManager:W *:S");
    }
    private static void sendTouch(Instrumentation runner,Activity host,int id,android.graphics.Rect bounds,long at,int action) {
        android.view.MotionEvent event=android.view.MotionEvent.obtain(at,
            action==android.view.MotionEvent.ACTION_DOWN?at:android.os.SystemClock.uptimeMillis(),
            action,bounds.exactCenterX(),bounds.exactCenterY(),0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try {runner.sendPointerSync(event);}
        catch(RuntimeException failure) {
            throw new AssertionError("Input injection failed action="+action+"; "+failure+"; "+read(runner,()->touchState(host,id)),failure);
        } finally {event.recycle();}
    }
    private static String touchState(Activity host,int id) {
        View view=host.findViewById(id);android.graphics.Rect bounds=new android.graphics.Rect();
        StringBuilder detail=new StringBuilder("target=").append(host.getResources().getResourceEntryName(id))
            .append(" activity=").append(host.getClass().getSimpleName())
            .append(" focus=").append(host.hasWindowFocus()).append(" pip=").append(host.isInPictureInPictureMode())
            .append(" finishing=").append(host.isFinishing()).append(" destroyed=").append(host.isDestroyed());
        if(view==null)return detail.append(" view=null").toString();
        detail.append(" attached=").append(view.isAttachedToWindow()).append(" shown=").append(view.isShown())
            .append(" enabled=").append(view.isEnabled()).append(" windowVisibility=").append(view.getWindowVisibility())
            .append(" visibleRect=").append(view.getGlobalVisibleRect(bounds)).append(" bounds=").append(bounds);
        int depth=0;
        for(View node=view;node!=null;) {
            detail.append(" ancestor[").append(depth++).append("]=").append(node.getClass().getSimpleName())
                .append(" laidOut=").append(node.isLaidOut()).append(" layoutRequested=").append(node.isLayoutRequested());
            android.view.ViewParent parent=node.getParent();node=parent instanceof View?(View)parent:null;
        }
        return detail.toString();
    }
    private static void assertReleased(PreviewActivity host) {
        if(keepsScreenOn(host))throw new AssertionError("Stopped preview retained screen hold");
        if(field(field(host,"haptics"),"requested")!=Detection.Risk.CAPTURED)throw new AssertionError("Stopped preview retained tactile feedback");
        for(String name:new String[]{"camera","session","surface","depthCamera","analysisTicket","activeTone","pendingCapture","pendingReadback","brightnessReadback"})
            if(field(host,name)!=null)throw new AssertionError("Resource retained: "+name);
        if(state(host).recognition || basicAt(host)!=-1)throw new AssertionError("Stopped camera retained recognition or feedback");
        if((boolean)field(host,"readingTexture"))throw new AssertionError("Stopped camera retained readback state");
    }
    private static void checkedNode(CheckBox view) {
        if(!view.isAttachedToWindow())throw new AssertionError("Checkbox semantics need an attached window");
        AccessibilityNodeInfo node=view.createAccessibilityNodeInfo();
        try {
            if(!node.isCheckable() || node.isChecked()!=view.isChecked() || node.isEnabled()!=view.isEnabled()
                || !android.text.TextUtils.equals(view.getText(),node.getText()))
                throw new AssertionError("Checkbox node mismatch: label="+node.getText()+" expected="+view.getText()
                    +" checkable="+node.isCheckable()+" checked="+node.isChecked()+"/"+view.isChecked()
                    +" enabled="+node.isEnabled()+"/"+view.isEnabled());
        } finally {node.recycle();}
    }
    private static void assertModelRetained(PreviewActivity host,InferenceWorker.Ticket ticket,Object model) {
        if(field(host,"analysisTicket")!=ticket || field(field(host,"worker"),"detector")!=model)
            throw new AssertionError("Geometry change reloaded the recognition model");
    }
    private static void assertNotPolling(PreviewActivity host) {
        if(Build.VERSION.SDK_INT>=29 && ((Handler)field(host,"main")).hasCallbacks((Runnable)field(host,"watchdog")))
            throw new AssertionError("Watchdog kept polling a stopped preview");
    }
    private static void assertWatching(PreviewActivity host) {
        if(Build.VERSION.SDK_INT>=29 && !((Handler)field(host,"main")).hasCallbacks((Runnable)field(host,"watchdog")))
            throw new AssertionError("Active preview lost watchdog protection");
    }
    private static void await(Instrumentation runner,BooleanSupplier condition,String label) throws Exception {
        awaitUntil(runner,condition,label,System.nanoTime()+10_000_000_000L);
    }
    private static long remainingMs(long deadline) {return deadline==Long.MAX_VALUE?Long.MAX_VALUE:Math.max(0,(deadline-System.nanoTime())/1_000_000);}
    private static void awaitUntil(Instrumentation runner,BooleanSupplier condition,String label,long deadline) throws Exception {
        while(System.nanoTime()<deadline){if(read(runner,condition::getAsBoolean))return;Thread.sleep(50);}
        throw new AssertionError("Timed out: "+label+"; interactive="+runner.getTargetContext().getSystemService(PowerManager.class).isInteractive()
            +" keyguard="+runner.getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked()+"; "+PreviewActivity.report());
    }
    private static void checkpoint(Instrumentation runner,String stage) {
        Bundle status=new Bundle();status.putString("stream","PIP_STAGE "+stage+" elapsedMs="+android.os.SystemClock.elapsedRealtime()+"\n");runner.sendStatus(0,status);
    }
    private interface Value<T>{T get();}
    private static <T>T read(Instrumentation runner,Value<T> value) {
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runner.runOnMainSync(()->{try{result.set(value.get());}catch(Throwable e){error.set(e);}});
        if(error.get()!=null)throw new AssertionError(error.get());return result.get();
    }
    static void screenshot(Instrumentation runner,String name) throws Exception {
        Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("Screenshot unavailable");
        File dir=new File(runner.getTargetContext().getCacheDir(),"ui-probe");if(!dir.isDirectory() && !dir.mkdirs())throw new IllegalStateException("Evidence directory unavailable");
        try(FileOutputStream stream=new FileOutputStream(new File(dir,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}finally{bitmap.recycle();}
    }
    private static void failureEvidence(Instrumentation runner,String prefix,PreviewActivity preview) {
        if(preview!=null)try {
            String incident=read(runner,()->((SessionDiagnostics)field(preview,"diagnostics")).report(
                BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,Build.MANUFACTURER,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT));
            SupportProbe.write(runner,prefix+"-problem-info-"+android.os.Process.myPid()+"-"+android.os.SystemClock.elapsedRealtime()+".txt",incident);
        }catch(Throwable ignored){/* Preserve the original failure if incident capture fails. */}
        // Capture before finish()/instrumentation teardown changes the focused window.
        try{screenshot(runner,prefix+".png");}catch(Exception ignored){/* Keep the original failure. */}
        try{powerEvidence(runner,prefix+"-routing.txt",shellText(runner,
            "logcat -d -s InputDispatcher:V InputManager:V InputManager-JNI:V WindowManager:I *:S"));}
        catch(Throwable ignored){/* Keep the original failure. */}
        for(String service:new String[]{"window windows","input","activity activities"}) {
            String suffix=service.split(" ",2)[0];
            try {
                File directory=new File(runner.getTargetContext().getCacheDir(),"ui-probe");
                if(!directory.isDirectory() && !directory.mkdirs())continue;
                try(ParcelFileDescriptor pipe=runner.getUiAutomation().executeShellCommand("dumpsys -t 3 "+service);
                    FileInputStream input=new FileInputStream(pipe.getFileDescriptor());
                    FileOutputStream output=new FileOutputStream(new File(directory,prefix+"-"+suffix+".txt"))) {
                    byte[] buffer=new byte[8192];int count,total=0;
                    while((count=input.read(buffer))!=-1) {
                        int used=Math.min(count,4*1024*1024-total);
                        output.write(buffer,0,used);total+=used;
                        if(total>=4*1024*1024)break;
                    }
                }
            }catch(Exception ignored){/* Evidence must not replace the original failure. */}
        }
    }
    private static void shell(Instrumentation runner,String command) {
        try(ParcelFileDescriptor pipe=runner.getUiAutomation().executeShellCommand(command);FileInputStream input=new FileInputStream(pipe.getFileDescriptor())) {
            byte[] buffer=new byte[1024];while(input.read(buffer)!=-1){/* drain */}
        } catch(Exception error){throw new AssertionError(error);}
    }
    private static String shellText(Instrumentation runner,String command) {
        try(ParcelFileDescriptor pipe=runner.getUiAutomation().executeShellCommand(command);
            FileInputStream input=new FileInputStream(pipe.getFileDescriptor())) {
            return new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }catch(Exception error){throw new AssertionError(error);}
    }
    private static SessionState state(PreviewActivity host){return (SessionState)field(host,"state");}
    private static long basicAt(PreviewActivity host){return (long)field(field(host,"feedback"),"basicAt");}
    private static boolean live(PreviewActivity host) {
        return state(host).phase==SessionState.Phase.LIVE && host.findViewById(R.id.preview_cover).getVisibility()==View.GONE
            && host.findViewById(R.id.enable_depth).isEnabled();
    }
    private static Object field(Object host,String name) {
        try{Field f=host.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(host);}
        catch(Exception error){throw new AssertionError(error);}
    }
}
