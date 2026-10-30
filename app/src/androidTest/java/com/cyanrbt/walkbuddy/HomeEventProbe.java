package com.cyanrbt.walkbuddy;

import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.widget.TextView;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Called only by the emulator-restricted Home/PiP probes, with real Activity and AppOps events. */
final class HomeEventProbe {
    static void idle(Instrumentation runner,MainActivity home,StringBuilder report)throws Exception {
        AppOpsManager ops=home.getSystemService(AppOpsManager.class);
        int original=ops.unsafeCheckOpRawNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE,Process.myUid(),home.getPackageName());
        try(Spy spy=new Spy(runner,home)) {
            requireWatcher(runner,home);quiet(runner,home,spy,1100);
            try {
                shell(runner,"appops set "+home.getPackageName()+" PICTURE_IN_PICTURE ignore");
                await(runner,()->((TextView)home.findViewById(R.id.state_pip)).getText().toString().equals(home.getString(R.string.pip_disabled)),"live AppOps denial reaches idle home");
                shell(runner,"appops set "+home.getPackageName()+" PICTURE_IN_PICTURE allow");
                await(runner,()->((TextView)home.findViewById(R.id.state_pip)).getText().toString().equals(home.getString(R.string.pip_available)),"live AppOps grant reaches idle home");
            } finally {shell(runner,"appops set "+home.getPackageName()+" PICTURE_IN_PICTURE "+mode(original));}
            await(runner,()->ops.unsafeCheckOpRawNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE,Process.myUid(),home.getPackageName())==original,"original raw AppOps mode restored");
            await(runner,()->((TextView)home.findViewById(R.id.state_pip)).getText().toString().equals(home.getString(PipSupport.available(home)?R.string.pip_available:R.string.pip_disabled)),"restored AppOps mode rendered");
            if(PreviewActivity.hasPreview())throw new AssertionError("Permission observation opened the camera");
            report.append("HOME_EVENTS_IDLE_PASS quietMs=1100 refreshes=0; actual AppOps deny/allow rendered without resume; original raw mode restored\n");
        }
    }

    static void retired(Instrumentation runner,MainActivity old,MainActivity current,
                        AppOpsManager.OnOpChangedListener retired,StringBuilder report)throws Exception {
        try(Spy oldSpy=new Spy(runner,old);Spy currentSpy=new Spy(runner,current)) {
            read(runner,()->{
                if((boolean)field(old,"visible") || field(old,"pipWatcher")!=null
                    || ((Collection<?>)field(PreviewActivity.homeState,"listeners")).contains(field(old,"stateChanged")))
                    throw new AssertionError("Retired home retained observers");return true;
            });
            runner.waitForIdleSync();int before=read(runner,()->currentSpy.calls);
            Thread stale=new Thread(()->retired.onOpChanged(AppOpsManager.OPSTR_PICTURE_IN_PICTURE,current.getPackageName()),"retired-appops-probe");
            stale.start();stale.join(3000);if(stale.isAlive())throw new AssertionError("Retired callback blocked");
            runner.waitForIdleSync();
            read(runner,()->{if(oldSpy.calls!=0 || currentSpy.calls!=before)throw new AssertionError("Retired callback refreshed an Activity");return true;});
            requireWatcher(runner,current);
            report.append("HOME_EVENTS_LIFECYCLE_PASS retired subscriptions removed; stale off-main AppOps callback ignored; recreated home subscribed\n");
        }
    }

    static void live(Instrumentation runner,MainActivity home,PreviewActivity preview,StringBuilder report)throws Exception {
        try(Spy spy=new Spy(runner,home)) {
            requireWatcher(runner,home);runner.waitForIdleSync();Thread.sleep(200);runner.waitForIdleSync();
            long[] before=read(runner,()->{SessionState state=(SessionState)field(preview,"state");return new long[]{state.captureAt,state.displayAt};});
            String stable=PreviewActivity.report();quiet(runner,home,spy,1100);
            read(runner,()->{
                SessionState state=(SessionState)field(preview,"state");
                if(state.phase!=SessionState.Phase.LIVE || state.captureAt<=before[0] || state.displayAt<=before[1]
                    || PreviewActivity.report()!=stable)throw new AssertionError("Quiet home did not retain an advancing live camera and cached report");return true;
            });
            int calls=read(runner,()->{
                int n=spy.calls;Object owner=field(preview,"homeOwner");
                for(int i=0;i<20;i++)PreviewActivity.homeState.historyChanged(owner);
                if(spy.calls!=n)throw new AssertionError("Home painted inside preview publication");return n;
            });
            runner.waitForIdleSync();
            read(runner,()->{if(spy.calls!=calls+1)throw new AssertionError("20 updates were not coalesced to one access sample");return true;});
            // Exercise the actual compatibility branch when no AppOps watcher is attached.
            read(runner,()->{invoke(home,"unwatchPip");invoke(home,"requestRefresh");return true;});
            runner.waitForIdleSync();int fallbackStart=read(runner,()->spy.calls);Thread.sleep(1100);
            read(runner,()->{
                if(spy.calls<fallbackStart+2 || !queued(home))throw new AssertionError("Visible live fallback lost permission polling");
                invoke(home,"watchPip");invoke(home,"requestRefresh");return true;
            });
            requireWatcher(runner,home);quiet(runner,home,spy,600);
            report.append("HOME_EVENTS_LIVE_PASS quietMs=1100 refreshes=0 advancingCapture=true advancingDisplay=true reportReused=true batch=20 accessSamples=1; watcher-unavailable polling fallback and reattachment pass\n");
        }
    }

    private static String mode(int value) {
        switch(value){case AppOpsManager.MODE_ALLOWED:return "allow";case AppOpsManager.MODE_IGNORED:return "ignore";
            case AppOpsManager.MODE_ERRORED:return "deny";case AppOpsManager.MODE_DEFAULT:return "default";
            case AppOpsManager.MODE_FOREGROUND:return "foreground";default:throw new AssertionError("Unknown original AppOps mode "+value);}
    }
    private static void requireWatcher(Instrumentation runner,MainActivity home)throws Exception {
        await(runner,()->(boolean)field(home,"visible") && field(home,"pipWatcher")!=null && !queued(home),"visible home with AppOps observer and drained refresh");
    }
    private static void quiet(Instrumentation runner,MainActivity home,Spy spy,long millis)throws Exception {
        runner.waitForIdleSync();await(runner,()->!queued(home),"home refresh drained");
        int before=read(runner,()->spy.calls);Thread.sleep(millis);
        read(runner,()->{if(spy.calls!=before || queued(home))throw new AssertionError("Stable home kept querying permissions: "+(spy.calls-before));return true;});
    }
    private static boolean queued(MainActivity home)throws Exception{return ((Handler)field(home,"handler")).hasCallbacks((Runnable)field(home,"refresh"));}
    private static void shell(Instrumentation runner,String command)throws Exception {
        try(ParcelFileDescriptor fd=runner.getUiAutomation().executeShellCommand(command);FileInputStream in=new FileInputStream(fd.getFileDescriptor())){
            while(in.read()!=-1){}
        }
    }
    private static final class Spy implements AutoCloseable,Supplier<MainActivity.Access> {
        final Instrumentation runner;final MainActivity home;final Supplier<MainActivity.Access> delegate;int calls;
        @SuppressWarnings("unchecked") Spy(Instrumentation runner,MainActivity home) {
            this.runner=runner;this.home=home;delegate=read(runner,()->(Supplier<MainActivity.Access>)field(home,"accessReader"));
            read(runner,()->{set(home,"accessReader",this);return true;});
        }
        public MainActivity.Access get(){calls++;return delegate.get();}
        public void close(){read(runner,()->{set(home,"accessReader",delegate);return true;});}
    }
    private interface Value<T>{T get()throws Exception;}
    private static <T>T read(Instrumentation runner,Value<T> fn) {
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runner.runOnMainSync(()->{try{result.set(fn.get());}catch(Throwable t){error.set(t);}});
        if(error.get()!=null)throw new AssertionError(error.get());return result.get();
    }
    private static void await(Instrumentation runner,Value<Boolean> fn,String reason)throws Exception {
        long end=System.nanoTime()+5_000_000_000L;
        while(System.nanoTime()<end){if(read(runner,fn))return;Thread.sleep(25);}throw new AssertionError("Timed out: "+reason);
    }
    private static Object field(Object host,String name)throws Exception{Field f=host.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(host);}
    private static void set(Object host,String name,Object value)throws Exception{Field f=host.getClass().getDeclaredField(name);f.setAccessible(true);f.set(host,value);}
    private static void invoke(Object host,String name)throws Exception{Method m=host.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(host);}
}
