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
        Bundle result=new Bundle();Activity home=null;PreviewActivity preview=null;
        StringBuilder report=new StringBuilder();int code=Activity.RESULT_CANCELED;
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("PiP recovery probe is restricted to emulators");
            if(!PipSupport.available(runner.getTargetContext()))throw new IllegalStateException("System PiP is unavailable");
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
                    bringForward(runner,host);
                    await(runner,()->(boolean)field(host,"resumed") && host.getSystemService(PowerManager.class).isInteractive()
                        && !host.getSystemService(KeyguardManager.class).isKeyguardLocked(),"recovery wake and unlock");
                    read(runner,()->{assertReleased(host);return true;});
                    tap(runner,host,R.id.restart_preview);
                    await(runner,()->live(host),"manual camera restart after recovery lock");
                    tap(runner,host,R.id.enter_pip);
                    await(runner,host::isInPictureInPictureMode,"PiP after recovery lock");
                    report.append("PASS recovery lock/wake precondition\n");
                }
                await(runner,()->(boolean)field(launcher,"visible"),"home visible behind PiP");
                read(runner,()->{
                    Handler handler=(Handler)field(host,"main");Runnable watchdog=(Runnable)field(host,"watchdog");
                    handler.removeCallbacks(watchdog);
                    state(host).captureAt=android.os.SystemClock.elapsedRealtime()-1201;
                    watchdog.run();assertReleased(host);assertNotPolling(host);
                    return true;
                });
                tap(runner,launcher,R.id.start);
                await(runner,()->!host.isInPictureInPictureMode() && (boolean)field(host,"resumed"),"recovery expansion");
                read(runner,()->{assertReleased(host);return true;});
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
                tap(runner,host,R.id.enter_pip);
                await(runner,host::isInPictureInPictureMode,"immediate PiP re-entry cycle="+cycle);
                long started=android.os.SystemClock.elapsedRealtime();
                await(runner,()->live(host) && state(host).captureAt>started+300 && state(host).displayAt>started+300,"recovery PiP frames advance");
                report.append("PASS immediate PiP recovery cycle=").append(cycle).append('\n');
            }
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
            failureEvidence(runner,"pip-recovery-failure");
            result.putString("stream",report+"PIP_RECOVERY_PROBE_FAIL: "+error+"; "+PreviewActivity.report());
        } finally {
            shell(runner,"input keyevent KEYCODE_WAKEUP");
            if(preview!=null){PreviewActivity host=preview;runner.runOnMainSync(host::finish);}
            if(home!=null){Activity host=home;runner.runOnMainSync(host::finish);}
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

            // Force expiry from the actual TextureView.draw -> SurfaceTextureUpdated path.
            AtomicBoolean expiredInDraw=new AtomicBoolean();
            TextureView retired=read(runner,()->{
                TextureView view=(TextureView)field(host,"texture");
                TextureView.SurfaceTextureListener delegate=view.getSurfaceTextureListener();
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
            checkpoint(runner,"recognition_results");
            read(runner,()->{checkedNode((CheckBox)host.findViewById(R.id.preview_recognition));checkedNode((CheckBox)host.findViewById(R.id.preview_sound));return true;});
            report.append("PASS attached checkbox labels, checked and enabled states before/during actual detection\n");
            InferenceWorker.Ticket oldTicket=read(runner,()->(InferenceWorker.Ticket)field(host,"analysisTicket"));
            read(runner,()->{
                host.findViewById(R.id.preview_recognition).performClick();
                if(!oldTicket.cancelled || field(host,"analysisTicket")!=null || basicAt(host)!=-1)
                    throw new AssertionError("Detection off retained analysis or feedback");
                if((boolean)field(host,"analysisReady") || (boolean)field(host,"analysisHasResult"))throw new AssertionError("Detection off retained readiness");
                return true;
            });
            checkpoint(runner,"recognition_off");
            read(runner,()->host.findViewById(R.id.preview_recognition).performClick());
            await(runner,()->live(host) && basicAt(host)>firstResult && field(host,"analysisTicket")!=oldTicket,"model restart after toggle");
            checkpoint(runner,"recognition_reenabled");
            report.append("PASS actual bundled-model results advance; off cancels Ticket/feedback; re-enable produces new results\n");
            InferenceWorker.Ticket geometryTicket=read(runner,()->(InferenceWorker.Ticket)field(host,"analysisTicket"));
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
            read(runner,()->{
                for(int id:new int[]{R.id.preview_header,R.id.preview_controls,R.id.preview_actions})
                    if(host.findViewById(id).getVisibility()!=View.GONE)throw new AssertionError("Full controls leaked into PiP");
                return true;
            });
            screenshot(runner,"pip-camera.png");
            read(runner,()->{assertModelRetained(host,geometryTicket,geometryModel);return true;});
            report.append("PASS actual system PiP, advancing capture/display/model results, full controls hidden\n");
            checkpoint(runner,"pip_analysis_live");
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
            read(runner,()->{assertModelRetained(host,geometryTicket,geometryModel);return true;});
            report.append("PASS expansion restores controls and keeps preview/model results LIVE\n");
            checkpoint(runner,"expanded_analysis_live");

            // Screen-off must stop even while the camera Activity was recently in PiP.
            shell(runner,"input keyevent KEYCODE_SLEEP");
            await(runner,()->state(host).phase==SessionState.Phase.UNAVAILABLE
                && !host.getSystemService(PowerManager.class).isInteractive() && !(boolean)field(host,"resumed")
                && !((Handler)field(host,"main")).hasCallbacks((Runnable)field(host,"watchdog")),"screen-off completes and stops camera");
            read(runner,()->{assertReleased(host);assertNotPolling(host);return true;});
            checkpoint(runner,"screen_off_complete");
            shell(runner,"input keyevent KEYCODE_WAKEUP");shell(runner,"wm dismiss-keyguard");
            bringForward(runner,host);
            await(runner,()->(boolean)field(host,"resumed") && host.getSystemService(PowerManager.class).isInteractive()
                && !host.getSystemService(KeyguardManager.class).isKeyguardLocked(),"wake, unlock and return to preview");
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
            read(runner,()->{
                try {
                    Method method=PreviewActivity.class.getDeclaredMethod("stopAction");method.setAccessible(true);
                    ((RemoteAction)method.invoke(host)).getActionIntent().send();
                } catch(Exception error) {throw new AssertionError(error);}
                return true;
            });
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
            await(runner,()->(boolean)field(launcher,"visible")
                && ((Handler)field(launcher,"handler")).hasCallbacks((Runnable)field(launcher,"refresh")),"home polls actual live PiP");
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
                && !((Handler)field(launcher,"handler")).hasCallbacks((Runnable)field(launcher,"refresh")),"home stops polling retained stopped PiP");
            Thread.sleep(350);
            read(runner,()->{
                assertNotPolling(idleHost);
                if(((Handler)field(launcher,"handler")).hasCallbacks((Runnable)field(launcher,"refresh")))throw new AssertionError("Idle home resumed polling");
                return launcher.findViewById(R.id.start).performClick();
            });
            await(runner,()->!idleHost.isInPictureInPictureMode() && (boolean)field(idleHost,"resumed"),"expand stopped PiP without restart");
            read(runner,()->{assertReleased(idleHost);assertNotPolling(idleHost);return idleHost.findViewById(R.id.restart_preview).performClick();});
            await(runner,()->live(idleHost),"manual restart after watchdog expiry");
            read(runner,()->{assertWatching(idleHost);return true;});
            long recoveredAt=android.os.SystemClock.elapsedRealtime();
            await(runner,()->live(idleHost) && state(idleHost).captureAt>recoveredAt+500
                && state(idleHost).displayAt>recoveredAt+500,"restarted camera keeps advancing with watchdog");
            report.append("PASS watchdog expiry and stale delivery leave visible stopped PiP/home idle; expansion stays stopped; manual restart re-arms protection\n");
            report.append("PIP_PROBE_PASS\n");
            result.putString("stream",report.toString());code=Activity.RESULT_OK;
        } catch(Throwable error) {
            failureEvidence(runner,"pip-failure");
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
        read(runner,()->{host.startActivity(new Intent(host,PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));return true;});
    }
    private static void tap(Instrumentation runner,Activity host,int id) throws Exception {
        // Real input respects focus and enabled state, unlike directly invoking performClick.
        try {await(runner,()->{
            View view=host.findViewById(id);android.graphics.Rect bounds=new android.graphics.Rect();
            if(view==null)return false;
            for(View node=view;node!=null;) {
                if(node.isLayoutRequested() || !node.isLaidOut())return false;
                android.view.ViewParent parent=node.getParent();node=parent instanceof View?(View)parent:null;
            }
            return host.hasWindowFocus() && view!=null && view.isShown() && view.isEnabled()
                && view.isAttachedToWindow() && view.getGlobalVisibleRect(bounds) && !bounds.isEmpty();
        },"touch target ready id="+id);} catch(AssertionError failure) {
            throw new AssertionError(failure.getMessage()+"; "+read(runner,()->touchState(host,id)),failure);
        }
        android.graphics.Rect bounds=read(runner,()->{
            android.graphics.Rect rect=new android.graphics.Rect();host.findViewById(id).getGlobalVisibleRect(rect);return rect;
        });
        android.util.Log.i("WalkBuddyInput","tap id="+host.getResources().getResourceEntryName(id)+" bounds="+bounds
            +" focus="+host.hasWindowFocus()+" pip="+host.isInPictureInPictureMode());
        long at=android.os.SystemClock.uptimeMillis();
        for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}) {
            android.view.MotionEvent event=android.view.MotionEvent.obtain(at,android.os.SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try{
                try {runner.sendPointerSync(event);} catch(RuntimeException failure) {
                    throw new AssertionError("Input injection failed action="+action+"; "+failure+"; "+read(runner,()->touchState(host,id)),failure);
                }
                if(action==android.view.MotionEvent.ACTION_DOWN)read(runner,()->{
                    android.graphics.Rect observed=new android.graphics.Rect();View current=host.findViewById(id);
                    if(current!=null)current.getGlobalVisibleRect(observed);
                    android.util.Log.i("WalkBuddyInput","after-down id="+host.getResources().getResourceEntryName(id)+" bounds="+observed
                        +" focus="+host.hasWindowFocus()+" pip="+host.isInPictureInPictureMode());return true;
                });
            }finally{event.recycle();}
        }
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
        for(String name:new String[]{"camera","session","surface","depthCamera","analysisTicket","activeTone","pendingCapture","pendingReadback"})
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
        long deadline=System.nanoTime()+10_000_000_000L;
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
    private static void screenshot(Instrumentation runner,String name) throws Exception {
        Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("Screenshot unavailable");
        File dir=new File(runner.getTargetContext().getCacheDir(),"ui-probe");if(!dir.isDirectory() && !dir.mkdirs())throw new IllegalStateException("Evidence directory unavailable");
        try(FileOutputStream stream=new FileOutputStream(new File(dir,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}finally{bitmap.recycle();}
    }
    private static void failureEvidence(Instrumentation runner,String prefix) {
        // Capture before finish()/instrumentation teardown changes the focused window.
        try{screenshot(runner,prefix+".png");}catch(Exception ignored){/* Keep the original failure. */}
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
