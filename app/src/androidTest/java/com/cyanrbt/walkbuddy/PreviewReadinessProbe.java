package com.cyanrbt.walkbuddy;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real Camera2 startup with held real PixelCopy delivery and controlled dark/error results. */
final class PreviewReadinessProbe {
    static void run(Instrumentation runner,PreviewActivity host,StringBuilder report) throws Exception {
        SharedPreferences history=host.getSharedPreferences(HomeJourney.PREFERENCES,Context.MODE_PRIVATE);
        boolean seen=history.getBoolean(HomeJourney.PREVIEW_SEEN,false);
        AtomicReference<Runnable> held=new AtomicReference<>();
        AtomicReference<Handler> callbackHandler=new AtomicReference<>();
        AtomicBoolean closing=new AtomicBoolean();
        try {
            prepare(runner,host,history,0,held,callbackHandler,closing);
            await(runner,()->held.get()!=null,"held actual first image result");
            String waiting=read(runner,()->{
                assertWaiting(host,history);
                return "phase="+state(host).phase+" captureAt="+state(host).captureAt+" displayAt="+state(host).displayAt
                    +" cover="+((TextView)host.findViewById(R.id.preview_cover)).getText()+"\n";
            });
            Runnable release=held.getAndSet(null);
            if(!callbackHandler.get().post(release))throw new AssertionError("Could not release real image check");
            await(runner,()->live(host),"checked image exposes preview");
            read(runner,()->{
                if(!history.getBoolean(HomeJourney.PREVIEW_SEEN,false) || !(boolean)field(host,"previewSeen")
                    || !host.findViewById(R.id.enter_pip).isEnabled() || state(host).recognition)
                    throw new AssertionError("Checked preview did not enable controls and first-use progress");
                return true;
            });
            SupportProbe.write(runner,"preview-readiness-held.txt",waiting+"released_real_image=true live=true progress=true\n");
            for(int mode:new int[]{1,2}) {
                prepare(runner,host,history,mode,held,callbackHandler,closing);
                await(runner,()->state(host).phase==SessionState.Phase.UNAVAILABLE,"invalid first image stops startup mode="+mode);
                read(runner,()->{
                    if(history.getBoolean(HomeJourney.PREVIEW_SEEN,false) || (boolean)field(host,"previewSeen")
                        || host.findViewById(R.id.preview_cover).getVisibility()!=View.VISIBLE
                        || host.findViewById(R.id.enter_pip).isEnabled() || host.findViewById(R.id.preview_recognition).isEnabled())
                        throw new AssertionError("Invalid first image was marked ready");
                    for(String name:new String[]{"camera","session","surface","depthCamera","brightnessReadback","analysisTicket","pendingReadback"})
                        if(field(host,name)!=null)throw new AssertionError("Invalid first image retained "+name);
                    SessionDiagnostics diagnostics=(SessionDiagnostics)field(host,"diagnostics");
                    if(diagnostics.preview()==null || diagnostics.preview().phase!=SessionState.Phase.STARTING
                        || !diagnostics.preview().category.equals(mode==1?"DARK":"GENERAL")
                        || diagnostics.preview().pipeline.brightness.status!=(mode==1?PixelCopy.SUCCESS:PixelCopy.ERROR_SOURCE_NO_DATA))
                        throw new AssertionError("Invalid first image was not diagnosed during startup");
                    return true;
                });
            }
            // A user-triggered retry gets a fresh production reader; no held/fake adapter remains.
            read(runner,()->host.findViewById(R.id.restart_preview).performClick());
            await(runner,()->live(host),"manual recovery after invalid first image");
            report.append("PREVIEW_READINESS_PASS cases=4 held_real_image=true covered_until_checked=true progress_after_check=true dark_and_error_stop_startup=true manual_recovery=true\n");
        } finally {
            closing.set(true);
            Runnable release=held.getAndSet(null);Handler handler=callbackHandler.get();
            if(release!=null && handler!=null)handler.post(release);
            read(runner,()->{history.edit().putBoolean(HomeJourney.PREVIEW_SEEN,seen).apply();set(host,"previewSeen",seen);return true;});
        }
    }
    private static void prepare(Instrumentation runner,PreviewActivity host,SharedPreferences history,int mode,
            AtomicReference<Runnable> held,AtomicReference<Handler> callbackHandler,AtomicBoolean closing) {
        await(runner,()->field(BrightnessReadback.class,"active")==null,"previous process copy completed");
        read(runner,()->{
            invoke(host,"fail",new Class<?>[]{String.class},"Controlled startup readiness reset");
            history.edit().putBoolean(HomeJourney.PREVIEW_SEEN,false).apply();set(host,"previewSeen",false);
            if(!host.findViewById(R.id.restart_preview).performClick() || state(host).phase!=SessionState.Phase.STARTING)
                throw new AssertionError("Manual startup was not accepted");
            invoke(host,"observeBrightness",new Class<?>[]{long.class},SystemClock.elapsedRealtime());
            BrightnessReadback first=(BrightnessReadback)field(host,"brightnessReadback");
            BrightnessReadback.Listener receiver=(BrightnessReadback.Listener)field(first,"listener");first.close();
            BrightnessReadback controlled=new BrightnessReadback(receiver,SystemClock::elapsedRealtime,(source,target,done,handler)->{
                if(mode==2){done.accept(PixelCopy.ERROR_SOURCE_NO_DATA);return;}
                PixelCopy.request(source,target,status->{
                    if(mode==1){if(status==PixelCopy.SUCCESS)target.eraseColor(Color.BLACK);done.accept(status);}
                    else {
                        callbackHandler.set(handler);held.set(()->done.accept(status));
                        if(closing.get()){Runnable release=held.getAndSet(null);if(release!=null)handler.post(release);}
                    }
                },handler);
            });
            set(host,"brightnessReadback",controlled);assertWaiting(host,history);return true;
        });
    }
    private static void assertWaiting(PreviewActivity host,SharedPreferences history) {
        if(state(host).phase!=SessionState.Phase.STARTING || host.findViewById(R.id.preview_cover).getVisibility()!=View.VISIBLE
            || host.findViewById(R.id.enter_pip).isEnabled() || host.findViewById(R.id.preview_recognition).isEnabled()
            || host.findViewById(R.id.enable_depth).isEnabled() || host.findViewById(R.id.viewfinder_toggle).isEnabled()
            || history.getBoolean(HomeJourney.PREVIEW_SEEN,false)
            || (boolean)field(host,"previewSeen"))throw new AssertionError("Unchecked startup exposed preview or completed progress");
    }
    private static boolean live(PreviewActivity host) {
        return state(host).phase==SessionState.Phase.LIVE && state(host).fresh(SystemClock.elapsedRealtime())
            && host.findViewById(R.id.preview_cover).getVisibility()==View.GONE;
    }
    private static SessionState state(PreviewActivity host){return (SessionState)field(host,"state");}
    private static Object field(Object source,String name) {
        try {Field f=(source instanceof Class<?>?(Class<?>)source:source.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(source instanceof Class<?>?null:source);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static void set(Object source,String name,Object value) {
        try{Field f=source.getClass().getDeclaredField(name);f.setAccessible(true);f.set(source,value);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static void invoke(Object source,String name,Class<?>[] types,Object... args) {
        try{Method m=source.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(source,args);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static <T>T read(Instrumentation runner,Callable<T> call) {
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        runner.runOnMainSync(()->{try{result.set(call.call());}catch(Throwable e){failure.set(e);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());return result.get();
    }
    private static void await(Instrumentation runner,BooleanSupplier condition,String label) {
        long until=SystemClock.elapsedRealtime()+8000;
        do{if(read(runner,condition::getAsBoolean))return;SystemClock.sleep(10);}while(SystemClock.elapsedRealtime()<until);
        throw new AssertionError("Timed out: "+label+"; "+PreviewActivity.report());
    }
}
