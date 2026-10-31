package com.cyanrbt.walkbuddy;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.CheckBox;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Actual Camera2 lifecycle with a controlled readback exception; no GPU stall root-cause claim. */
final class BrightnessActivityProbe {
    static void run(Instrumentation runner,PreviewActivity host,StringBuilder report) throws Exception {
        await(runner,()->live(host) && field(host,"brightnessReadback")!=null
            && finished(host),"initial brightness workspace");
        BrightnessReadback first=read(runner,()->(BrightnessReadback)field(host,"brightnessReadback"));
        Bitmap storage=read(runner,()->(Bitmap)field(first,"bitmap"));
        Object pixels=read(runner,()->field(first,"pixels"));
        boolean vibration=read(runner,()->((CheckBox)host.findViewById(R.id.preview_vibration)).isChecked());
        long at=SystemClock.elapsedRealtime();
        await(runner,()->live(host) && finished(host) && lastFrame(host)>=at,"second brightness check");
        read(runner,()->{
            if(field(host,"brightnessReadback")!=first || field(first,"bitmap")!=storage || field(first,"pixels")!=pixels
                || storage.isRecycled())throw new AssertionError("Live brightness copy reallocated/recycled workspace");
            ReadbackTiming.Sample timing=((ReadbackTiming)field(host,"readbackTiming")).snapshot(state(host).generation,
                SystemClock.elapsedRealtimeNanos(),android.os.Debug.threadCpuTimeNanos());
            BrightnessTiming.Sample async=first.snapshot(SystemClock.elapsedRealtime());
            if(timing.wallMs<0 || timing.cpuMs<0 || async.wallMs<0 || async.cpuMs<0 || async.queueMs<0 || async.deliveryMs<0)
                throw new AssertionError("Actual brightness readback timing missing");
            SupportProbe.write(runner,"brightness-live-timing.txt","taskWallMs="+timing.wallMs+" taskCpuMs="+timing.cpuMs
                +" asyncWallMs="+async.wallMs+" callerCpuMs="+async.cpuMs+" queueMs="+async.queueMs+" deliveryMs="+async.deliveryMs+"\n");
            return true;
        });
        BrightnessReadback.Listener receiver=read(runner,()->(BrightnessReadback.Listener)field(first,"listener"));
        BrightnessReadback broken=new BrightnessReadback(receiver,SystemClock::elapsedRealtime,(source,target,done,handler)->{
            long until=SystemClock.elapsedRealtimeNanos()+12_000_000;
            do{SystemClock.sleep(1);}while(SystemClock.elapsedRealtimeNanos()<until);
            throw new IllegalStateException("Injected asynchronous brightness copy failure");
        });
        read(runner,()->{first.close();set(host,"brightnessReadback",broken);broken.observe(SystemClock.elapsedRealtime());queue(host);return true;});
        await(runner,()->state(host).phase==SessionState.Phase.UNAVAILABLE && (boolean)field(broken,"closed"),"readback error releases camera");
        String evidence=read(runner,()->{
            if(!storage.isRecycled())throw new AssertionError("Retired brightness bitmap retained");
            for(String name:new String[]{"camera","session","surface","depthCamera","analysisTicket","pendingCapture","pendingReadback","brightnessReadback"})
                if(field(host,name)!=null)throw new AssertionError("Readback failure retained "+name);
            if((boolean)field(host,"readingTexture") || ((Handler)field(host,"main")).hasCallbacks((Runnable)field(host,"watchdog")))
                throw new AssertionError("Readback failure retained work/polling");
            SessionDiagnostics diagnostics=(SessionDiagnostics)field(host,"diagnostics");
            SessionDiagnostics.Pipeline pipeline=diagnostics.preview().pipeline;
            BrightnessTiming.Sample saved=pipeline.brightness;
            if(pipeline.readback.running || saved.stage!=BrightnessTiming.Stage.FINISHED || saved.wallMs<12 || saved.cpuMs<0
                || saved.queueMs<0 || saved.deliveryMs<0 || saved.status!=android.view.PixelCopy.ERROR_UNKNOWN)
                throw new AssertionError("Incident did not freeze completed asynchronous brightness wall/CPU/status");
            return diagnostics.report(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,
                Build.MANUFACTURER,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT);
        });
        SupportProbe.write(runner,"brightness-readback-failure-report.txt",evidence);
        SystemClock.sleep(150);
        read(runner,()->{
            if(state(host).phase!=SessionState.Phase.UNAVAILABLE || field(host,"camera")!=null)
                throw new AssertionError("Readback failure auto-restarted camera");
            return host.findViewById(R.id.restart_preview).performClick();
        });
        await(runner,()->live(host) && field(host,"brightnessReadback")!=null
            && finished(host),"manual camera and brightness restart");
        long restarted=SystemClock.elapsedRealtime();
        await(runner,()->live(host) && state(host).captureAt>restarted+300 && state(host).displayAt>restarted+300,"new brightness observation frames");
        read(runner,()->{
            Object next=field(host,"brightnessReadback");
            if(next==first || next==broken || field(next,"bitmap")==storage
                || state(host).recognition || ((CheckBox)host.findViewById(R.id.preview_sound)).isChecked()
                || ((CheckBox)host.findViewById(R.id.preview_vibration)).isChecked()!=vibration)
                throw new AssertionError("Restart reused retired workspace or changed default/remembered choices");
            return true;
        });
        report.append("BRIGHTNESS_ACTIVITY_PASS cases=3 reused=true close_on_error=true frozen_async_timing=true restart_new_workspace=true no_auto_camera=true\n");
    }
    private static boolean finished(PreviewActivity host) {
        BrightnessReadback reader=(BrightnessReadback)field(host,"brightnessReadback");
        return reader!=null && field(reader,"bitmap")!=null && reader.snapshot(SystemClock.elapsedRealtime()).stage==BrightnessTiming.Stage.FINISHED;
    }
    private static long lastFrame(PreviewActivity host) {
        return ((BrightnessFlight.Ticket)field(field(field(host,"brightnessReadback"),"last"),"ticket")).frameAt;
    }
    private static boolean live(PreviewActivity host) {
        return state(host).phase==SessionState.Phase.LIVE && state(host).fresh(SystemClock.elapsedRealtime())
            && host.findViewById(R.id.preview_cover).getVisibility()==View.GONE;
    }
    private static void queue(PreviewActivity host) {
        try{Method method=PreviewActivity.class.getDeclaredMethod("queueReadback",long.class);method.setAccessible(true);method.invoke(host,SystemClock.elapsedRealtime());}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static SessionState state(PreviewActivity host){return (SessionState)field(host,"state");}
    private static Object field(Object source,String name) {
        try{Field field=source.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(source);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static void set(Object source,String name,Object value) {
        try{Field field=source.getClass().getDeclaredField(name);field.setAccessible(true);field.set(source,value);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static <T>T read(Instrumentation runner,Callable<T> task) {
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        runner.runOnMainSync(()->{try{result.set(task.call());}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());return result.get();
    }
    private static void await(Instrumentation runner,BooleanSupplier condition,String label) {
        long until=SystemClock.elapsedRealtime()+8000;
        do {if(read(runner,condition::getAsBoolean))return;SystemClock.sleep(50);}while(SystemClock.elapsedRealtime()<until);
        throw new AssertionError("Timed out: "+label+"; "+PreviewActivity.report());
    }
}
