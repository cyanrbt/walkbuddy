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
    static void run(Instrumentation runner) {
        Bundle result=new Bundle();Activity home=null;PreviewActivity preview=null;
        StringBuilder report=new StringBuilder();int code=Activity.RESULT_CANCELED;
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("PiP probe is restricted to emulators");
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
                if(((CheckBox)host.findViewById(R.id.preview_recognition)).isChecked()
                    || ((CheckBox)host.findViewById(R.id.preview_sound)).isChecked())throw new AssertionError("Detection/sound must default off");
                if(!host.getSharedPreferences(HomeJourney.PREFERENCES,0).getBoolean(HomeJourney.PREVIEW_SEEN,false))
                    throw new AssertionError("Fresh camera preview was not recorded");
                if(!host.findViewById(R.id.enable_depth).isEnabled())throw new AssertionError("LIVE camera cannot switch mode");
                return true;
            });
            report.append("PASS Camera2 capture and display are LIVE; default detection/sound off\n");
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
            read(runner,()->{assertReleased(host);if(state(host).phase==SessionState.Phase.LIVE)throw new AssertionError("Return auto-started camera");return true;});
            read(runner,()->{if(host.findViewById(R.id.enable_depth).isEnabled())throw new AssertionError("Stopped preview allows mode switching");return true;});
            read(runner,()->host.findViewById(R.id.restart_preview).performClick());
            await(runner,()->live(host),"manual restart after Home");
            report.append("PASS Home releases camera; returning requires manual restart\n");
            checkpoint(runner,"home_restart_live");

            read(runner,()->{
                host.findViewById(R.id.preview_recognition).performClick();
                if((boolean)field(host,"analysisReady") || !((android.widget.TextView)host.findViewById(R.id.preview_info)).getText().toString().contains("准备识别器"))
                    throw new AssertionError("Cold detection did not show preparation state");
                return true;
            });
            checkpoint(runner,"recognition_on");
            await(runner,()->live(host) && basicAt(host)>0,"first actual model result");
            long firstResult=read(runner,()->basicAt(host));
            await(runner,()->live(host) && basicAt(host)>firstResult+400,"repeated actual model results");
            checkpoint(runner,"recognition_results");
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

            // Expand the existing PiP Activity, the same route used by Android's CTS PiP app.
            bringForward(runner,host);
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
                .append(read(runner,()->field(host,"deferredCaptureCount"))).append("\nPIP_PROBE_PASS\n");
            result.putString("stream",report.toString());code=Activity.RESULT_OK;
        } catch(Throwable error) {
            try{screenshot(runner,"pip-failure.png");}catch(Exception ignored){/* Preserve the original failure. */}
            result.putString("stream",report+"PIP_PROBE_FAIL: "+error+"; "+PreviewActivity.report());
        } finally {
            shell(runner,"input keyevent KEYCODE_WAKEUP");
            if(preview!=null){PreviewActivity host=preview;runner.runOnMainSync(host::finish);}
            if(home!=null){Activity host=home;runner.runOnMainSync(host::finish);}
        }
        runner.finish(code,result);
    }

    private static void bringForward(Instrumentation runner,PreviewActivity host) {
        read(runner,()->{host.startActivity(new Intent(host,PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));return true;});
    }
    private static void assertReleased(PreviewActivity host) {
        for(String name:new String[]{"camera","session","surface","depthCamera","analysisTicket","activeTone","pendingCapture","pendingReadback"})
            if(field(host,name)!=null)throw new AssertionError("Resource retained: "+name);
        if(state(host).recognition || basicAt(host)!=-1)throw new AssertionError("Stopped camera retained recognition or feedback");
        if((boolean)field(host,"readingTexture"))throw new AssertionError("Stopped camera retained readback state");
    }
    private static void assertModelRetained(PreviewActivity host,InferenceWorker.Ticket ticket,Object model) {
        if(field(host,"analysisTicket")!=ticket || field(field(host,"worker"),"detector")!=model)
            throw new AssertionError("Geometry change reloaded the recognition model");
    }
    private static void assertNotPolling(PreviewActivity host) {
        if(Build.VERSION.SDK_INT>=29 && ((Handler)field(host,"main")).hasCallbacks((Runnable)field(host,"watchdog")))
            throw new AssertionError("Watchdog kept polling off-screen");
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
