package com.cyanrbt.walkbuddy;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual emulated Camera2/PiP and delivered PendingIntents; no physical-device acceptance. */
final class PipStopProbe {
    static final class Evidence {
        final JSONArray cases=new JSONArray();
        final String filename="pip-stop-"+android.os.Process.myPid()+"-"+SystemClock.elapsedRealtime()+".json";
        void add(Instrumentation runner,String name,JSONObject before,JSONObject after)throws Exception {
            cases.put(new JSONObject().put("case",name).put("before",before).put("after",after));
            SupportProbe.write(runner,filename,new JSONObject().put("scope","Actual emulator PiP and RemoteAction PendingIntent delivery; deliberate failure, not natural timeout reproduction")
                .put("cases",cases).toString(2)+"\n");
        }
    }
    static RemoteAction capture(Instrumentation runner,PreviewActivity host) {
        return read(runner,()->{
            RemoteAction action=((PictureInPictureParams)field(host,"submittedPipParams")).getActions().get(0);
            if(!action.isEnabled() || !action.getTitle().toString().equals(host.getString(R.string.preview_stop)))
                throw new AssertionError("Current PiP menu lost its first stop action");
            return action;
        });
    }
    static void reject(Instrumentation runner,PreviewActivity host,RemoteAction action,String name,Evidence evidence)throws Exception {
        JSONObject before=read(runner,()->snapshot(host));
        Object camera=read(runner,()->field(host,"camera")),texture=read(runner,()->field(host,"texture"));
        Object ticket=read(runner,()->field(host,"analysisTicket")),brightness=read(runner,()->field(host,"brightnessReadback"));
        long at=SystemClock.elapsedRealtime();
        Intent command=deliver(runner,action);
        String owner=command.getStringExtra(StopPreviewReceiver.OWNER);long visit=command.getLongExtra(StopPreviewReceiver.PIP_VISIT,-1);
        if(name.equals("earlier_pip_visit") && (!before.getString("owner").equals(owner) || before.getLong("visit")==visit)
            || (name.equals("previous_observation") || name.equals("previous_activity")) && before.getString("owner").equals(owner)
            || name.equals("fullscreen_current_identity") && (before.getBoolean("pip") || !before.getString("owner").equals(owner) || before.getLong("visit")!=visit)
            || name.equals("legacy_without_owner_or_visit") && (owner!=null || visit!=-1))
            throw new AssertionError("Stop guard scenario did not exercise its intended identity: "+name);
        if(name.equals("other_owner_current_visit") && (before.getString("owner").equals(owner) || before.getLong("visit")!=visit || !before.getBoolean("pip")))
            throw new AssertionError("Wrong-owner case did not isolate ownership from visit and fullscreen");
        before.put("delivered_command",new JSONObject().put("owner",owner==null?JSONObject.NULL:owner).put("visit",visit)
            .put("action",command.getAction()).put("data",command.getData()==null?JSONObject.NULL:command.getData().toString()));
        await(runner,()->live(host) && state(host).captureAt>at+200 && state(host).displayAt>at+200,"frames after ignored stop "+name);
        JSONObject after=read(runner,()->{
            if(field(host,"camera")!=camera || field(host,"texture")!=texture || field(host,"analysisTicket")!=ticket
                || field(host,"brightnessReadback")!=brightness || state(host).generation!=before.getLong("generation")
                || state(host).recognition!=before.getBoolean("recognition")
                || !field(host,"pipOwner").equals(before.getString("owner")) || host.isFinishing() || host.isDestroyed())
                throw new AssertionError("Obsolete stop changed current observation: "+name);
            return snapshot(host);
        });
        evidence.add(runner,name,before,after);
    }
    static void rejectFullscreen(Instrumentation runner,PreviewActivity host,RemoteAction oldAction,Evidence evidence)throws Exception {
        reject(runner,host,oldAction,"expanded_old_menu",evidence);
        RemoteAction currentIdentity=read(runner,()->{
            Method method=PreviewActivity.class.getDeclaredMethod("stopAction");method.setAccessible(true);
            return (RemoteAction)method.invoke(host);
        });
        reject(runner,host,currentIdentity,"fullscreen_current_identity",evidence);
    }
    static void rejectLegacy(Instrumentation runner,PreviewActivity host,Evidence evidence)throws Exception {
        RemoteAction legacy=read(runner,()->new RemoteAction(Icon.createWithResource(host,R.drawable.ic_stop),"Legacy stop","Legacy stop",
            PendingIntent.getBroadcast(host,0,new Intent(host,StopPreviewReceiver.class).setAction(StopPreviewReceiver.STOP_PREVIEW),
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)));
        reject(runner,host,legacy,"legacy_without_owner_or_visit",evidence);
    }
    static void rejectOtherOwner(Instrumentation runner,PreviewActivity host,Evidence evidence)throws Exception {
        RemoteAction current=capture(runner,host);
        RemoteAction retired=read(runner,()->{
            Field owner=host.getClass().getDeclaredField("pipOwner");owner.setAccessible(true);Object original=owner.get(host);
            try {
                owner.set(host,"retired-owner");
                Method method=PreviewActivity.class.getDeclaredMethod("stopAction");method.setAccessible(true);
                return (RemoteAction)method.invoke(host);
            } finally {owner.set(host,original);}
        });
        if(current.getActionIntent().equals(retired.getActionIntent()))throw new AssertionError("Owner-only change aliased the current stop PendingIntent");
        reject(runner,host,retired,"other_owner_current_visit",evidence);
    }
    static void stopFailed(Instrumentation runner,PreviewActivity host,Evidence evidence)throws Exception {
        PipProbe.tap(runner,host,R.id.enter_pip);
        await(runner,host::isInPictureInPictureMode,"final stop-failure PiP entry");
        RemoteAction liveAction=capture(runner,host);
        JSONObject before=read(runner,()->{
            JSONObject current=snapshot(host);
            Method fail=PreviewActivity.class.getDeclaredMethod("fail",String.class);fail.setAccessible(true);
            fail.invoke(host,"Controlled stop-menu recovery check");
            if(state(host).phase!=SessionState.Phase.UNAVAILABLE || host.isFinishing() || !host.isInPictureInPictureMode())
                throw new AssertionError("Controlled failure did not retain stopped PiP");
            for(String field:new String[]{"camera","session","surface","depthCamera","brightnessReadback","analysisTicket"})
                if(field(host,field)!=null)throw new AssertionError("Stopped PiP retained "+field);
            return current;
        });
        RemoteAction stoppedAction=capture(runner,host);
        if(!liveAction.getActionIntent().equals(stoppedAction.getActionIntent()))throw new AssertionError("Failure invalidated this observation's stop identity");
        JSONObject failed=read(runner,()->snapshot(host));
        PipProbe.screenshot(runner,"pip-stop-controlled-failure.png");
        // Deliberately deliver the actual LIVE menu action after its frame generation has expired.
        Intent command=deliver(runner,liveAction);
        if(!failed.getString("owner").equals(command.getStringExtra(StopPreviewReceiver.OWNER))
            || failed.getLong("visit")!=command.getLongExtra(StopPreviewReceiver.PIP_VISIT,-1))
            throw new AssertionError("Current failed PiP stop lost its observation identity");
        await(runner,host::isDestroyed,"current stop closes failed PiP");
        JSONObject after=read(runner,()->snapshot(host));after.put("failed_before_stop",failed);
        evidence.add(runner,"current_action_stops_failed_observation",before,after);
    }
    private static Intent deliver(Instrumentation runner,RemoteAction action)throws Exception {
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);AtomicReference<Intent> result=new AtomicReference<>();
        action.getActionIntent().send(runner.getTargetContext(),0,null,(pending,intent,code,data,extras)->{
            result.set(new Intent(intent));done.countDown();
        },new Handler(Looper.getMainLooper()));
        if(!done.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Stop PendingIntent delivery did not finish");
        return result.get();
    }
    static void finished(Instrumentation runner,Evidence evidence,StringBuilder report)throws Exception {
        if(evidence.cases.length()!=8)throw new AssertionError("Incomplete PiP stop ownership checks: "+evidence.cases.length());
        report.append("PIP_STOP_OWNERSHIP_PASS cases=8 fullscreen_ignored=true old_visit_ignored=true old_observation_ignored=true old_activity_ignored=true legacy_ignored=true owner_only_distinct=true failed_pip_stoppable=true advancing_frames=true\n");
    }
    private static JSONObject snapshot(PreviewActivity host)throws Exception {
        return new JSONObject().put("elapsed_ms",SystemClock.elapsedRealtime()).put("owner",field(host,"pipOwner"))
            .put("visit",field(host,"pipSoundVisit")).put("generation",state(host).generation).put("phase",state(host).phase.name())
            .put("pip",host.isInPictureInPictureMode()).put("finishing",host.isFinishing()).put("destroyed",host.isDestroyed())
            .put("capture_at",state(host).captureAt).put("display_at",state(host).displayAt).put("recognition",state(host).recognition)
            .put("camera",field(host,"camera")!=null).put("analysis",field(host,"analysisTicket")!=null);
    }
    private static boolean live(PreviewActivity host) {return !host.isFinishing() && !host.isDestroyed()
        && state(host).phase==SessionState.Phase.LIVE && state(host).fresh(SystemClock.elapsedRealtime());}
    private static void await(Instrumentation runner,BooleanSupplier condition,String label)throws Exception {
        long end=SystemClock.elapsedRealtime()+6000;
        while(SystemClock.elapsedRealtime()<end){if(read(runner,condition::getAsBoolean))return;Thread.sleep(25);}
        throw new AssertionError("Timed out: "+label+"; "+PreviewActivity.report());
    }
    private static <T>T read(Instrumentation runner,Callable<T> value) {
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runner.runOnMainSync(()->{try{result.set(value.call());}catch(Throwable e){error.set(e);}});
        if(error.get()!=null)throw new AssertionError(error.get());return result.get();
    }
    private static SessionState state(PreviewActivity host) {try{return (SessionState)field(host,"state");}catch(Exception error){throw new AssertionError(error);}}
    private static Object field(Object host,String name)throws Exception {Field f=host.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(host);}
}
