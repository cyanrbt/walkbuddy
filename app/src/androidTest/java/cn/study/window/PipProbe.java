package cn.study.window;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.RemoteAction;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.CheckBox;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Opt-in emulator integration probe. Requires a working, non-black Camera2 preview. */
final class PipProbe {
    static void run(Instrumentation runner) {
        Bundle result = new Bundle();
        Activity home = null;
        PreviewActivity preview = null;
        try {
            if (!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("PiP probe is restricted to emulators");
            if (!PipSupport.available(runner.getTargetContext()))
                throw new IllegalStateException("Enable system PiP for the test app before this probe");
            try (ParcelFileDescriptor pipe = runner.getUiAutomation().executeShellCommand(
                    "pm grant " + runner.getTargetContext().getPackageName() + " android.permission.CAMERA");
                 FileInputStream input = new FileInputStream(pipe.getFileDescriptor())) {
                byte[] bytes = new byte[1024];
                while (input.read(bytes) != -1) { /* Wait for the grant command to complete. */ }
            }
            home = runner.startActivitySync(new Intent(runner.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Instrumentation.ActivityMonitor monitor = runner.addMonitor(PreviewActivity.class.getName(), null, false);
            Activity launcher = home;
            runner.runOnMainSync(() -> launcher.findViewById(R.id.start).performClick());
            preview = (PreviewActivity) runner.waitForMonitorWithTimeout(monitor, 5000);
            runner.removeMonitor(monitor);
            if (preview == null) throw new AssertionError("Preview did not open from the home button");
            PreviewActivity host = preview;
            await(runner, () -> state(host).phase == SessionState.Phase.LIVE, "camera LIVE");
            runner.runOnMainSync(() -> {
                if (((CheckBox) host.findViewById(R.id.preview_recognition)).isChecked()
                        || ((CheckBox) host.findViewById(R.id.preview_sound)).isChecked())
                    throw new AssertionError("Recognition and sound must default off");
                host.findViewById(R.id.enter_pip).performClick();
            });
            await(runner, host::isInPictureInPictureMode, "system PiP entry");
            long firstCapture = readOnMain(runner, () -> state(host).captureAt);
            await(runner, () -> state(host).captureAt > firstCapture + 500
                && state(host).phase == SessionState.Phase.LIVE, "camera keeps advancing in PiP");
            runner.runOnMainSync(() -> {
                if (host.findViewById(R.id.preview_controls).getVisibility() != View.GONE)
                    throw new AssertionError("Full-screen controls leaked into PiP");
                try {
                    Method method = PreviewActivity.class.getDeclaredMethod("stopAction");
                    method.setAccessible(true);
                    RemoteAction action = (RemoteAction) method.invoke(host);
                    action.getActionIntent().send();
                } catch (Exception error) { throw new AssertionError(error); }
            });
            await(runner, host::isDestroyed, "PiP stop action closes Activity");
            runner.runOnMainSync(() -> {
                for (String field : new String[]{"camera", "session", "surface", "analysisTicket", "activeTone"})
                    if (field(host, field) != null) throw new AssertionError("Resource retained: " + field);
            });
            result.putString("stream", "PIP_PROBE_PASS: explicit entry, advancing camera in PiP, hidden controls, stop releases resources");
            runner.finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "PIP_PROBE_FAIL: " + error + "; " + PreviewActivity.report());
            runner.finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (preview != null) { PreviewActivity host = preview; runner.runOnMainSync(host::finish); }
            if (home != null) { Activity host = home; runner.runOnMainSync(host::finish); }
        }
    }

    private static void await(Instrumentation runner, BooleanSupplier condition, String label) throws Exception {
        long deadline = System.nanoTime() + 7_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (readOnMain(runner, condition::getAsBoolean)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out: " + label + "; " + PreviewActivity.report());
    }

    private interface Value<T> { T get(); }
    private static <T> T readOnMain(Instrumentation runner, Value<T> value) {
        AtomicReference<T> result = new AtomicReference<>();
        runner.runOnMainSync(() -> result.set(value.get()));
        return result.get();
    }
    private static SessionState state(PreviewActivity host) { return (SessionState) field(host, "state"); }
    private static Object field(Object host, String name) {
        try {
            Field field = PreviewActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(host);
        } catch (Exception error) { throw new AssertionError(error); }
    }
}
