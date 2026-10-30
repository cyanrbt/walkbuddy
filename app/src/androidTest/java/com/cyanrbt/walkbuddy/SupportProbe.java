package com.cyanrbt.walkbuddy;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ScrollView;
import android.widget.CheckBox;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Last probe: real clipboard/Activity actions on an emulator, after the other input checks. */
final class SupportProbe {
    static void run(Instrumentation runner) {
        Bundle result=new Bundle();PreviewActivity activity=null;int code=Activity.RESULT_CANCELED;
        boolean touchedVibration=false,hadVibration=false,oldVibration=true;
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("Support probe is restricted to emulators");
            if(runner.getTargetContext().checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                throw new IllegalStateException("Revoke CAMERA before starting support instrumentation");
            activity=(PreviewActivity)runner.startActivitySync(new Intent(runner.getTargetContext(),PreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            PreviewActivity host=activity;
            await(runner,()->diagnostics(host).available() && state(host).phase==SessionState.Phase.UNAVAILABLE,"permission evidence");
            read(runner,()->{
                SessionDiagnostics.Snapshot first=diagnostics(host).preview();
                Method fail=PreviewActivity.class.getDeclaredMethod("fail",RecoveryGuide.Reason.class,String.class);fail.setAccessible(true);
                fail.invoke(host,RecoveryGuide.Reason.HIDDEN,"测试后续离屏");
                if(first!=diagnostics(host).preview() || !"PERMISSION".equals(first.category))throw new AssertionError("First cause was overwritten");
                View copy=host.findViewById(R.id.copy_problem_info);
                ((ScrollView)host.findViewById(R.id.preview_controls)).requestChildFocus(copy,copy);return true;
            });
            PipProbe.awaitPreviewInputReady(runner,host,"problem info copy");
            read(runner,()->{
                View copy=host.findViewById(R.id.copy_problem_info);Rect rect=new Rect();
                if(!copy.getGlobalVisibleRect(rect) || rect.height()!=copy.getHeight())throw new AssertionError("Copy action clipped");return true;
            });
            long generation=read(runner,()->state(host).generation);
            PipProbe.tap(runner,host,R.id.copy_problem_info);
            String copied=read(runner,()->{
                ClipboardManager clipboard=host.getSystemService(ClipboardManager.class);ClipData clip=clipboard.getPrimaryClip();
                if(clip==null || clip.getItemCount()!=1 || !clip.getDescription().hasMimeType("text/plain")
                    || clip.getItemAt(0).getUri()!=null || clip.getItemAt(0).getIntent()!=null)
                    throw new AssertionError("Copy did not create one plain-text item");
                String text=String.valueOf(clip.getItemAt(0).getText());
                String expected=diagnostics(host).report(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,
                    Build.MANUFACTURER,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT);
                if(!text.equals(expected) || !text.contains("分类：PERMISSION"))throw new AssertionError("Copied report differs from frozen evidence");
                if(state(host).generation!=generation || state(host).phase!=SessionState.Phase.UNAVAILABLE
                    || field(host,"camera")!=null || field(host,"analysisTicket")!=null)throw new AssertionError("Copy changed the camera/session");
                return text;
            });
            write(runner,"problem-info-report.txt",copied);
            runner.getUiAutomation().grantRuntimePermission(host.getPackageName(),Manifest.permission.CAMERA);
            Instrumentation.ActivityMonitor monitor=runner.addMonitor(PreviewActivity.class.getName(),null,false);
            try {
                runner.runOnMainSync(host::recreate);
                PreviewActivity replacement=(PreviewActivity)runner.waitForMonitorWithTimeout(monitor,7000);
                if(replacement==null || replacement==host)throw new AssertionError("Page did not recreate");
                activity=replacement;runner.waitForIdleSync();
                read(runner,()->{
                    if(!diagnostics(replacement).available() || state(replacement).phase!=SessionState.Phase.STOPPED
                        || state(replacement).generation!=0 || field(replacement,"camera")!=null
                        || replacement.findViewById(R.id.copy_problem_info).getVisibility()!=View.VISIBLE)
                        throw new AssertionError("Recreation lost evidence or opened camera");
                    String retained=diagnostics(replacement).report("unused",0,"unused","unused","unused",0);
                    if(!retained.contains("保留记录：页面重建前的信息") || !retained.contains("分类：PERMISSION"))
                        throw new AssertionError("Recreated report lost its provenance");
                    replacement.findViewById(R.id.restart_preview).performClick();return true;
                });
                await(runner,()->state(replacement).phase==SessionState.Phase.LIVE && state(replacement).fresh(SystemClock.elapsedRealtime()),"explicit camera restart");
                read(runner,()->{
                    if(diagnostics(replacement).available() || replacement.findViewById(R.id.copy_problem_info).getVisibility()!=View.GONE)
                        throw new AssertionError("New observation retained old evidence");return true;
                });
            } finally {runner.removeMonitor(monitor);}
            PreviewActivity current=activity;
            android.content.SharedPreferences preferences=current.getSharedPreferences(PreviewActivity.REMINDERS_PREFERENCES,0);
            hadVibration=preferences.contains(PreviewActivity.VIBRATION_PREFERENCE);
            oldVibration=preferences.getBoolean(PreviewActivity.VIBRATION_PREFERENCE,true);touchedVibration=true;
            String beforeReady=read(runner,()->vibrationState(current));
            write(runner,"support-vibration-ready.txt","before "+beforeReady+"\n");
            // Capture metadata can promote LIVE before the next view render. Require the rendered control too.
            long readyAt=SystemClock.uptimeMillis();
            await(runner,()->state(current).phase==SessionState.Phase.LIVE && state(current).fresh(SystemClock.elapsedRealtime())
                && current.findViewById(R.id.preview_cover).getVisibility()==View.GONE
                && current.findViewById(R.id.preview_vibration).isShown()
                && current.findViewById(R.id.preview_vibration).isEnabled(),"rendered vibration control after explicit restart");
            String afterReady=read(runner,()->vibrationState(current));
            write(runner,"support-vibration-ready.txt","before "+beforeReady+"\nafter "+afterReady
                +" waitMs="+(SystemClock.uptimeMillis()-readyAt)+"\n");
            String mutedReport=read(runner,()->{
                CheckBox toggle=current.findViewById(R.id.preview_vibration);
                Object camera=field(current,"camera");long muteGeneration=state(current).generation;
                if(!toggle.isEnabled() || !toggle.isChecked())throw new AssertionError("Missing default vibration control: "+vibrationState(current));
                toggle.performClick();
                if(toggle.isChecked() || ((RiskHaptics)field(current,"haptics")).enabled()
                    || preferences.getBoolean(PreviewActivity.VIBRATION_PREFERENCE,true)
                    || field(current,"camera")!=camera || state(current).generation!=muteGeneration)
                    throw new AssertionError("Muted choice did not persist independently");
                Method fail=PreviewActivity.class.getDeclaredMethod("fail",RecoveryGuide.Reason.class,String.class);fail.setAccessible(true);
                fail.invoke(current,RecoveryGuide.Reason.GENERAL,"构造关闭振动的问题信息");
                String text=diagnostics(current).report(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,
                    Build.MANUFACTURER,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT);
                if(!text.contains("振动：关"))throw new AssertionError("Incident report lost the actual vibration choice");return text;
            });
            write(runner,"vibration-muted-report.txt",mutedReport);
            Instrumentation.ActivityMonitor retainedMonitor=runner.addMonitor(PreviewActivity.class.getName(),null,false);
            try {
                runner.runOnMainSync(current::recreate);
                PreviewActivity retained=(PreviewActivity)runner.waitForMonitorWithTimeout(retainedMonitor,7000);
                if(retained==null || retained==current)throw new AssertionError("Vibration page did not recreate");
                activity=retained;runner.waitForIdleSync();
                read(runner,()->{
                    if(state(retained).phase!=SessionState.Phase.STOPPED || field(retained,"camera")!=null
                        || ((CheckBox)retained.findViewById(R.id.preview_vibration)).isChecked()
                        || ((RiskHaptics)field(retained,"haptics")).enabled())throw new AssertionError("Recreation lost the muted preference or opened camera");
                    if(!diagnostics(retained).report("unused",0,"unused","unused","unused",0).contains("振动：关"))
                        throw new AssertionError("Recreated incident changed the saved vibration choice");
                    retained.findViewById(R.id.restart_preview).performClick();return true;
                });
                await(runner,()->state(retained).phase==SessionState.Phase.LIVE && state(retained).fresh(SystemClock.elapsedRealtime()),"muted new observation");
                read(runner,()->{
                    CheckBox toggle=retained.findViewById(R.id.preview_vibration);
                    if(toggle.isChecked() || ((RiskHaptics)field(retained,"haptics")).enabled())throw new AssertionError("New observation forgot mute");
                    Object camera=field(retained,"camera");long enabledGeneration=state(retained).generation;toggle.performClick();
                    if(!toggle.isChecked() || !preferences.getBoolean(PreviewActivity.VIBRATION_PREFERENCE,false)
                        || field(retained,"camera")!=camera || state(retained).generation!=enabledGeneration)
                        throw new AssertionError("Re-enabling vibration changed the camera or failed to persist");return true;
                });
            } finally {runner.removeMonitor(retainedMonitor);}
            String vibrationEvidence="VIBRATION_PERSISTENCE_PASS cases=3; CheckBox mute keeps camera, actual Activity recreation and new manual observation preserve mute, explicit enable persists independently\n";
            write(runner,"vibration-preference.txt",vibrationEvidence);
            result.putString("stream",vibrationEvidence+"SUPPORT_PROBE_PASS cases=4; first cause, native plain-text clipboard without restart, saved report survives recreation, explicit restart clears; virtual Camera2\n");
            code=Activity.RESULT_OK;
        } catch(Throwable error) {result.putString("stream","SUPPORT_PROBE_FAIL: "+error);}
        finally {
            if(touchedVibration){
                android.content.SharedPreferences.Editor editor=runner.getTargetContext().getSharedPreferences(PreviewActivity.REMINDERS_PREFERENCES,0).edit();
                if(hadVibration)editor.putBoolean(PreviewActivity.VIBRATION_PREFERENCE,oldVibration);else editor.remove(PreviewActivity.VIBRATION_PREFERENCE);
                editor.apply();
            }
            if(activity!=null){PreviewActivity host=activity;runner.runOnMainSync(()->{
                try {ClipboardManager clipboard=host.getSystemService(ClipboardManager.class);if(clipboard!=null)clipboard.clearPrimaryClip();}
                catch(RuntimeException ignored){/* Preserve the original probe result. */}
                finally {host.finish();}
            });}
        }
        runner.finish(code,result);
    }
    static void write(Instrumentation runner,String name,String text) throws Exception {
        File directory=new File(runner.getTargetContext().getCacheDir(),"ui-probe");
        if(!directory.isDirectory() && !directory.mkdirs())throw new AssertionError("Support evidence directory unavailable");
        try(FileOutputStream output=new FileOutputStream(new File(directory,name))){output.write(text.getBytes(StandardCharsets.UTF_8));}
    }
    private static SessionState state(PreviewActivity host){return (SessionState)field(host,"state");}
    private static String vibrationState(PreviewActivity host) {
        CheckBox toggle=host.findViewById(R.id.preview_vibration);
        return "phase="+state(host).phase+" fresh="+state(host).fresh(SystemClock.elapsedRealtime())
            +" cover="+host.findViewById(R.id.preview_cover).getVisibility()+" shown="+toggle.isShown()
            +" enabled="+toggle.isEnabled()+" checked="+toggle.isChecked()
            +" preference="+host.getSharedPreferences(PreviewActivity.REMINDERS_PREFERENCES,0).getBoolean(PreviewActivity.VIBRATION_PREFERENCE,true);
    }
    private static SessionDiagnostics diagnostics(PreviewActivity host){return (SessionDiagnostics)field(host,"diagnostics");}
    private static Object field(Object host,String name) {
        try {Field value=host.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(host);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private interface Value<T>{T get() throws Exception;}
    private static <T>T read(Instrumentation runner,Value<T> value) {
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        runner.runOnMainSync(()->{try{result.set(value.get());}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());return result.get();
    }
    private static void await(Instrumentation runner,BooleanSupplier condition,String label) throws Exception {
        long end=SystemClock.uptimeMillis()+10000;
        do {if(read(runner,condition::getAsBoolean))return;Thread.sleep(50);}while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Timed out: "+label);
    }
}
