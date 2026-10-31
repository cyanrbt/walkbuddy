package com.cyanrbt.walkbuddy;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.widget.TextView;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Real Activity permission/settings/recreation path on an emulator, without starting a camera. */
final class RecoveryProbe {
    static void run(Instrumentation runner) {
        Bundle result=new Bundle();Activity activity=null;int resultCode=Activity.RESULT_CANCELED;
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("Recovery probe is restricted to emulators");
            if(runner.getTargetContext().checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                throw new IllegalStateException("Revoke CAMERA before starting this instrumentation, not inside its live process");
            activity=runner.startActivitySync(new Intent(runner.getTargetContext(),PreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity host=activity;
            runner.waitForIdleSync();
            checkOnMain(runner,()->{
                assertNoCamera(host);
                RecoveryPresentationProbe.full((android.view.View)host.findViewById(R.id.preview_content).getParent(),
                    RecoveryGuide.resolve(RecoveryGuide.Reason.PERMISSION,false));
                if(host.findViewById(R.id.enable_depth).isEnabled())throw new AssertionError("Stopped camera still allows mode switching");
                if(!((TextView)host.findViewById(R.id.restart_preview)).getText().toString().contains("设置"))
                    throw new AssertionError("Missing permission still offers camera retry");
            });
            Thread.sleep(350);
            checkOnMain(runner,()->assertNoCamera(host));
            IntentFilter settingsFilter=new IntentFilter(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            settingsFilter.addDataScheme("package");
            Instrumentation.ActivityMonitor settings=runner.addMonitor(settingsFilter,
                new Instrumentation.ActivityResult(Activity.RESULT_OK,null),true);
            try {
                checkOnMain(runner,()->host.findViewById(R.id.restart_preview).performClick());
                if(settings.getHits()!=1)throw new AssertionError("Recovery button did not open application settings");
                checkOnMain(runner,()->assertNoCamera(host));
            } finally {runner.removeMonitor(settings);}
            try(ParcelFileDescriptor pipe=runner.getUiAutomation().executeShellCommand(
                    "pm grant "+runner.getTargetContext().getPackageName()+" android.permission.CAMERA");
                FileInputStream input=new FileInputStream(pipe.getFileDescriptor())) {
                byte[] buffer=new byte[1024];while(input.read(buffer)!=-1){/* Wait for the grant. */}
            }
            if(runner.getTargetContext().checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
                throw new AssertionError("Test permission grant did not complete");
            Instrumentation.ActivityMonitor recreated=runner.addMonitor(PreviewActivity.class.getName(),null,false);
            try {
                runner.runOnMainSync(host::recreate);
                Activity replacement=runner.waitForMonitorWithTimeout(recreated,7000);
                if(replacement==null || replacement==host)throw new AssertionError("Activity did not recreate");
                activity=replacement;
                runner.waitForIdleSync();
                checkOnMain(runner,()->{
                    assertNoCamera(replacement);
                    RecoveryPresentationProbe.full((android.view.View)replacement.findViewById(R.id.preview_content).getParent(),
                        RecoveryGuide.resolve(RecoveryGuide.Reason.HIDDEN,true));
                    try {
                        Field field=PreviewActivity.class.getDeclaredField("state");field.setAccessible(true);
                        SessionState state=(SessionState)field.get(replacement);
                        if(state.phase!=SessionState.Phase.STOPPED || state.generation!=0)
                            throw new AssertionError("Activity recreation attempted to start a camera session");
                    } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
                    TextView button=replacement.findViewById(R.id.restart_preview);
                    if(!button.isEnabled() || !button.getText().toString().contains("重新开启"))
                        throw new AssertionError("Grant/recreation did not leave an explicit restart action");
                });
                Thread.sleep(350);
                checkOnMain(runner,()->assertNoCamera(replacement));
            } finally {runner.removeMonitor(recreated);}
            result.putString("stream","RECOVERY_PROBE_PASS: denied permission, settings intent, grant and real Activity recreation; no camera auto-start or stopped watchdog polling\n");
            resultCode=Activity.RESULT_OK;
        } catch(Throwable error) {
            result.putString("stream","RECOVERY_PROBE_FAIL: "+error);
        } finally {
            if(activity!=null){Activity host=activity;runner.runOnMainSync(host::finish);}
        }
        runner.finish(resultCode,result);
    }

    private static void assertNoCamera(Activity activity) {
        try {
            for(String name:new String[]{"camera","session","surface","depthCamera","analysisTicket","pendingCapture","pendingReadback","brightnessReadback"}) {
                Field f=PreviewActivity.class.getDeclaredField(name);f.setAccessible(true);
                if(f.get(activity)!=null)throw new AssertionError("Resource opened unexpectedly: "+name);
            }
            Field handlerField=PreviewActivity.class.getDeclaredField("main");handlerField.setAccessible(true);
            Field taskField=PreviewActivity.class.getDeclaredField("watchdog");taskField.setAccessible(true);
            android.os.Handler handler=(android.os.Handler)handlerField.get(activity);
            Runnable watchdog=(Runnable)taskField.get(activity);
            if(Build.VERSION.SDK_INT>=29 && handler.hasCallbacks(watchdog))throw new AssertionError("Stopped preview kept polling");
            watchdog.run(); // A stale delivery must not start a new callback chain.
            if(Build.VERSION.SDK_INT>=29 && handler.hasCallbacks(watchdog))throw new AssertionError("Stale watchdog re-armed a stopped preview");
        } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
    }
    private static void checkOnMain(Instrumentation runner,Runnable check) {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        runner.runOnMainSync(()->{try{check.run();}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());
    }
}
