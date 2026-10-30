package com.cyanrbt.walkbuddy;

import android.app.Instrumentation;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.CheckBox;
import java.lang.reflect.*;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Emulator camera plus controlled audio lifecycle; no claim about audibility or physical latency. */
final class ToneProbe {
    private static final class Metrics {
        final AtomicInteger creates=new AtomicInteger(),plays=new AtomicInteger(),closes=new AtomicInteger(),accepted=new AtomicInteger();
        final StringBuilder operations=new StringBuilder();
        final AtomicBoolean onMain=new AtomicBoolean();
        synchronized void mark(String operation) {
            if(Looper.myLooper()==Looper.getMainLooper())onMain.set(true);
            operations.append(operation).append(" thread=").append(Thread.currentThread().getName())
                .append(" at_ms=").append(SystemClock.elapsedRealtime()).append('\n');
        }
    }
    static void run(Instrumentation runner,PreviewActivity host,StringBuilder report) throws Exception {
        TonePlayer original=read(runner,()->(TonePlayer)field(host,"toneWorker"));
        read(runner,()->{sound(host).setChecked(false);return true;});
        if(!original.awaitIdle(3000))throw new AssertionError("Previous audio cleanup did not finish");
        Object camera=read(runner,()->field(host,"camera"));
        InferenceWorker.Ticket ticket=read(runner,()->(InferenceWorker.Ticket)field(host,"analysisTicket"));
        RiskHaptics haptics=read(runner,()->(RiskHaptics)field(host,"haptics"));
        HapticsProbe.Output tactile=new HapticsProbe.Output();RiskHaptics recorded=new RiskHaptics(tactile);
        recorded.setEnabled(haptics.enabled());
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Metrics blockedMetrics=new Metrics(),nativeMetrics=new Metrics();
        TonePlayer blocked=new TonePlayer(SystemClock::elapsedRealtime,()->{
            blockedMetrics.mark("create_blocked");blockedMetrics.creates.incrementAndGet();entered.countDown();
            if(!release.await(6,TimeUnit.SECONDS))throw new IllegalStateException("Audio probe gate timeout");
            return new TonePlayer.Output(){
                public boolean play(){blockedMetrics.mark("play");blockedMetrics.plays.incrementAndGet();return true;}
                public void stop(){blockedMetrics.mark("stop");}
                public void close(){blockedMetrics.mark("close");blockedMetrics.closes.incrementAndGet();}
            };
        });
        TonePlayer broken=new TonePlayer(SystemClock::elapsedRealtime,()->{throw new IllegalStateException("Injected audio resource failure");});
        TonePlayer nativePlayer=new TonePlayer(SystemClock::elapsedRealtime,()->{
            nativeMetrics.mark("create_begin");AndroidTone tone=new AndroidTone();nativeMetrics.mark("create_end");nativeMetrics.creates.incrementAndGet();
            return new TonePlayer.Output(){
                public boolean play(){nativeMetrics.mark("play_begin");boolean ok=tone.play();nativeMetrics.mark("play_end accepted="+ok);
                    nativeMetrics.plays.incrementAndGet();if(ok)nativeMetrics.accepted.incrementAndGet();return ok;}
                public void stop(){nativeMetrics.mark("stop");tone.stop();}
                public void close(){nativeMetrics.mark("close_begin");try{tone.close();}finally{nativeMetrics.closes.incrementAndGet();nativeMetrics.mark("close_end");}}
            };
        });
        try {
            read(runner,()->{set(host,"toneWorker",blocked);set(host,"haptics",recorded);sound(host).setChecked(true);return true;});
            if(!entered.await(2,TimeUnit.SECONDS))throw new AssertionError("Audio preparation did not start");
            read(runner,()->{constructAlert(host);return true;});
            long start=SystemClock.elapsedRealtime();
            await(runner,()->state(host).phase==SessionState.Phase.LIVE && state(host).captureAt>start+300 && state(host).displayAt>start+300,
                "camera advances while audio initialization is blocked");
            read(runner,()->{
                sound(host).setChecked(false);
                if(field(host,"activeTone")!=null || ticket.cancelled || field(host,"analysisTicket")!=ticket
                    || field(host,"camera")!=camera || !state(host).recognition || release.getCount()!=1)
                    throw new AssertionError("Sound-off blocked or changed detection/camera");
                return true;
            });
            release.countDown();if(!blocked.awaitIdle(3000))throw new AssertionError("Cancelled initializer did not close");
            if(blockedMetrics.plays.get()!=0 || blockedMetrics.closes.get()!=1 || blockedMetrics.onMain.get())
                throw new AssertionError("Blocked audio played late or used main thread");
            report.append("TONE_BLOCKED_ACTIVITY_PASS camera_advances=true late_plays=0 closes=1\n");

            read(runner,()->{set(host,"toneWorker",broken);sound(host).setChecked(true);return true;});
            await(runner,()->!sound(host).isChecked(),"audio failure disables only sound");
            read(runner,()->{
                if(!state(host).recognition || ticket.cancelled || field(host,"analysisTicket")!=ticket || field(host,"camera")!=camera
                    || recorded.enabled()!=haptics.enabled() || state(host).phase!=SessionState.Phase.LIVE)
                    throw new AssertionError("Audio failure changed other live features");
                int plays=tactile.plays;constructAlert(host);
                if(recorded.enabled() && tactile.plays!=plays+1)throw new AssertionError("Audio failure disabled tactile dispatch");
                if(((DetectionFeedback)field(host,"feedback")).visible(SystemClock.elapsedRealtime()).detections.isEmpty())
                    throw new AssertionError("Audio failure erased visual results");
                return true;
            });
            report.append("TONE_FAILURE_ACTIVITY_PASS sound_off=true detection_camera_tactile_retained=true\n");

            read(runner,()->{set(host,"toneWorker",nativePlayer);sound(host).setChecked(true);return true;});
            if(!nativePlayer.awaitIdle(4000))throw new AssertionError("Native audio initialization did not settle");
            TonePlayer.Session nativeSession=read(runner,()->(TonePlayer.Session)field(host,"activeTone"));
            if(nativeSession!=null && !nativeSession.failed) {
                nativePlayer.play(nativeSession,SystemClock.elapsedRealtime()+1000);
                if(!nativePlayer.awaitIdle(3000))throw new AssertionError("Native audio playback did not settle");
                if(!nativeSession.failed) {
                    SystemClock.sleep(200);nativePlayer.play(nativeSession,SystemClock.elapsedRealtime()+1000);
                    if(!nativePlayer.awaitIdle(3000))throw new AssertionError("Second native audio playback did not settle");
                }
            }
            boolean unavailable=nativeSession==null || nativeSession.failed;
            if(unavailable)await(runner,()->!sound(host).isChecked(),"native unavailable fallback");
            else if(nativeMetrics.plays.get()!=2 || nativeMetrics.accepted.get()!=2)
                throw new AssertionError("Available native audio did not exercise two accepted play requests");
            read(runner,()->{sound(host).setChecked(false);return true;});
            if(!nativePlayer.awaitIdle(3000))throw new AssertionError("Native audio release did not settle");
            if(nativeMetrics.onMain.get() || nativeMetrics.creates.get()>1 || nativeMetrics.closes.get()!=nativeMetrics.creates.get())
                throw new AssertionError("Native audio thread or resource ownership mismatch");
            read(runner,()->{
                if(!state(host).recognition || state(host).phase!=SessionState.Phase.LIVE || field(host,"camera")!=camera || ticket.cancelled)
                    throw new AssertionError("Native audio changed live camera/detection");return true;
            });
            report.append("TONE_NATIVE_ACTIVITY_PASS creates=").append(nativeMetrics.creates).append(" plays=").append(nativeMetrics.plays)
                .append(" accepted=").append(nativeMetrics.accepted).append(" closes=").append(nativeMetrics.closes)
                .append(" all_off_main=true unavailable=").append(unavailable).append("; emulator API outcome only, no audibility claim\n");
            SupportProbe.write(runner,"tone-threading.txt","BLOCKED\n"+blockedMetrics.operations+"NATIVE\n"+nativeMetrics.operations);
        } finally {
            release.countDown();
            read(runner,()->{sound(host).setChecked(false);set(host,"toneWorker",original);recorded.clear();set(host,"haptics",haptics);
                ((DetectionFeedback)field(host,"feedback")).clear();((RiskAnnouncer)field(host,"announcer")).clear();set(host,"pendingSpeak",false);return true;});
            for(TonePlayer player:new TonePlayer[]{blocked,broken,nativePlayer}){player.shutdown();if(!player.awaitIdle(4000))throw new AssertionError("Audio test worker did not shut down");}
        }
    }
    private static void constructAlert(PreviewActivity host) {
        DetectionFeedback feedback=(DetectionFeedback)field(host,"feedback");feedback.clear();
        long now=SystemClock.elapsedRealtime();Detection near=new Detection("构造音频检查",1,.3f,.1f,.7f,.9f,Detection.Risk.NEAR,.7f,null);
        for(long at:new long[]{now-2,now-1})feedback.depth(new DepthSnapshot(at,Collections.singletonList(near),"构造音频检查",DepthSnapshot.Stage.MEASURING,true,true),0,at);
        invoke(host,"showFeedback",new Class<?>[]{long.class},now);invoke(host,"alert",new Class<?>[0]);
    }
    private static CheckBox sound(PreviewActivity host){return host.findViewById(R.id.preview_sound);}
    private static SessionState state(PreviewActivity host){return (SessionState)field(host,"state");}
    private static Object field(Object host,String name){try{Field f=host.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(host);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void set(Object host,String name,Object value){try{Field f=host.getClass().getDeclaredField(name);f.setAccessible(true);f.set(host,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void invoke(Object host,String name,Class<?>[] types,Object...args){try{Method m=host.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(host,args);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static <T>T read(Instrumentation runner,Callable<T> call) {
        AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runner.runOnMainSync(()->{try{value.set(call.call());}catch(Throwable e){error.set(e);}});
        if(error.get()!=null)throw new AssertionError(error.get());return value.get();
    }
    private static void await(Instrumentation runner,BooleanSupplier condition,String label) {
        long until=SystemClock.elapsedRealtime()+8000;
        do{if(read(runner,condition::getAsBoolean))return;SystemClock.sleep(50);}while(SystemClock.elapsedRealtime()<until);
        throw new AssertionError("Timed out: "+label+"; "+PreviewActivity.report());
    }
}
