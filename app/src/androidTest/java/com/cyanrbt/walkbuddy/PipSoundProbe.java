package com.cyanrbt.walkbuddy;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.Intent;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.CheckBox;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.*;

/** Emulator-only caller: real SystemUI touches and PendingIntents, controlled audio output. */
final class PipSoundProbe {
    static void run(Instrumentation runner,PreviewActivity host,Activity home,StringBuilder report)throws Exception {
        UiAutomation automation=runner.getUiAutomation();AccessibilityServiceInfo info=automation.getServiceInfo();int flags=info.flags;
        info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        TonePlayer original=read(runner,()->(TonePlayer)field(host,"toneWorker"));
        AtomicInteger creates=new AtomicInteger(),plays=new AtomicInteger(),closes=new AtomicInteger();AtomicBoolean mainThread=new AtomicBoolean();
        TonePlayer controlled=new TonePlayer(SystemClock::elapsedRealtime,()->{
            if(Looper.myLooper()==Looper.getMainLooper())mainThread.set(true);creates.incrementAndGet();
            return new TonePlayer.Output(){public boolean play(){plays.incrementAndGet();return true;}public void stop(){}public void close(){closes.incrementAndGet();}};
        });
        AtomicInteger failures=new AtomicInteger();
        TonePlayer broken=new TonePlayer(SystemClock::elapsedRealtime,()->{failures.incrementAndGet();throw new IllegalStateException("Injected PiP audio failure");});
        StringBuilder trace=new StringBuilder("Actual SystemUI touch targets; controlled output, no physical audibility claim\n");
        Object camera=read(runner,()->field(host,"camera")),texture=read(runner,()->field(host,"texture"));
        InferenceWorker.Ticket ticket=read(runner,()->(InferenceWorker.Ticket)field(host,"analysisTicket"));
        RiskHaptics tactile=read(runner,()->(RiskHaptics)field(host,"haptics"));boolean vibration=read(runner,()->vibration(host).isChecked());
        try {
            read(runner,()->{sound(host).setChecked(false);return true;});
            if(!original.awaitIdle(3000))throw new AssertionError("Original audio did not drain");
            read(runner,()->{set(host,"toneWorker",controlled);return true;});
            if(host.getMaxNumPictureInPictureActions()<3)throw new AssertionError("Required emulator has fewer than three PiP action slots");
            RemoteAction oldEnable=read(runner,()->soundAction(host,false));
            read(runner,()->{
                StopPreviewReceiver receiver=new StopPreviewReceiver();
                for(int invalid=0;invalid<6;invalid++) {
                    Intent command=new Intent(StopPreviewReceiver.SET_SOUND)
                        .putExtra(StopPreviewReceiver.OWNER,invalid==0?"retired-owner":(String)field(host,"pipOwner"))
                        .putExtra(StopPreviewReceiver.GENERATION,state(host).generation-(invalid==1?1:0))
                        .putExtra(StopPreviewReceiver.SOUND_REVISION,(long)field(host,"soundRevision")-(invalid==2?1:0))
                        .putExtra(StopPreviewReceiver.PIP_VISIT,(long)field(host,"pipSoundVisit")-(invalid==3?1:0))
                        .putExtra(StopPreviewReceiver.SOUND_ENABLED,true);
                    if(invalid==4)command.removeExtra(StopPreviewReceiver.SOUND_ENABLED);
                    if(invalid==5)command.removeExtra(StopPreviewReceiver.SOUND_REVISION);
                    receiver.onReceive(host,command);
                    if(sound(host).isChecked())throw new AssertionError("Invalid sound command changed choice "+invalid);
                }
                return true;
            });
            tapMenuAction(runner,host,false,"pip-sound-menu-off.png",trace);
            await(runner,()->sound(host).isChecked(),"SystemUI enables only prompt tones");
            TonePlayer.Session session=read(runner,()->(TonePlayer.Session)field(host,"activeTone"));
            RemoteAction oldDisable=read(runner,()->soundAction(host,true));
            tapMenuAction(runner,host,true,"pip-sound-menu-on.png",trace);
            await(runner,()->!sound(host).isChecked(),"SystemUI disables prompt tones");
            if(!controlled.awaitIdle(3000))throw new AssertionError("Muted audio did not drain");
            read(runner,()->{if(session==null || !session.cancelled || field(host,"activeTone")!=null)throw new AssertionError("Mute retained the old audio session");return true;});
            long revision=read(runner,()->(long)field(host,"soundRevision"));
            PipProbe.sendAction(runner,oldEnable);PipProbe.sendAction(runner,oldDisable);
            read(runner,()->{if(sound(host).isChecked() || (long)field(host,"soundRevision")!=revision)throw new AssertionError("Old choice action replayed");return true;});
            tapMenuAction(runner,host,false,null,trace);
            await(runner,()->sound(host).isChecked(),"fresh SystemUI action re-enables tones");
            PipProbe.sendAction(runner,oldDisable);
            read(runner,()->{if(!sound(host).isChecked())throw new AssertionError("Old mute changed a newer choice");return true;});
            tapMenuAction(runner,host,true,null,trace);
            await(runner,()->!sound(host).isChecked(),"second SystemUI mute");
            if(!controlled.awaitIdle(3000))throw new AssertionError("Second mute did not drain");
            RemoteAction priorVisit=read(runner,()->soundAction(host,false));
            closeMenu(runner,host);
            int afterMute=plays.get();long mutedAt=SystemClock.elapsedRealtime();
            await(runner,()->state(host).captureAt>mutedAt+600 && state(host).displayAt>mutedAt+600 && state(host).analysisAt>mutedAt+600,"camera and model advance after PiP mute");
            if(plays.get()!=afterMute)throw new AssertionError("Muted PiP dispatched more audio");
            PipProbe.tap(runner,home,R.id.start);
            await(runner,()->!host.isInPictureInPictureMode() && host.hasWindowFocus(),"expand after sound controls");
            PipProbe.sendAction(runner,priorVisit);
            read(runner,()->{if(sound(host).isChecked() || !sound(host).getText().toString().equals(host.getString(R.string.check_sound)))throw new AssertionError("Full preview choice or label changed");return true;});
            int[] full=read(runner,()->{View root=(View)field(host,"root");return new int[]{root.getWidth(),root.getHeight()};});
            PipProbe.tap(runner,host,R.id.enter_pip);
            await(runner,()->{View root=(View)field(host,"root");return host.isInPictureInPictureMode()
                && root.getWidth()>0 && root.getHeight()>0 && root.getWidth()<full[0] && root.getHeight()<full[1];},"new actual PiP visit");
            PipProbe.sendAction(runner,priorVisit);
            read(runner,()->{
                if(sound(host).isChecked() || field(host,"camera")!=camera || field(host,"texture")!=texture
                    || field(host,"analysisTicket")!=ticket || ticket.cancelled || !state(host).recognition
                    || field(host,"haptics")!=tactile || vibration(host).isChecked()!=vibration)
                    throw new AssertionError("Sound command changed camera, model, vibration or a newer PiP visit");
                soundAction(host,false);return true;
            });
            RemoteAction beforeFailure=read(runner,()->{set(host,"toneWorker",broken);return soundAction(host,false);});
            PipProbe.sendAction(runner,beforeFailure);
            await(runner,()->failures.get()==1 && !sound(host).isChecked() && field(host,"activeTone")==null,"PiP audio failure disables only tones");
            PipProbe.sendAction(runner,beforeFailure);
            read(runner,()->{
                soundAction(host,false);
                if(sound(host).isChecked() || failures.get()!=1 || !state(host).recognition || ticket.cancelled
                    || field(host,"camera")!=camera || field(host,"texture")!=texture || vibration(host).isChecked()!=vibration)
                    throw new AssertionError("PiP audio failure or stale retry changed the pipeline");return true;
            });
            report.append("PIP_SOUND_FAILURE_MENU_PASS sound_off=true next_action=enable stale_retry_ignored=true detection_camera_vibration_retained=true\n");
            trace.append("guarded_invalid_commands=6 real_ui_toggles=4 stale_choice_fullscreen_visit=true muted_late_plays=0 camera_model_vibration_retained=true\n");
            report.append("PIP_SOUND_CONTROLS_PASS ui_toggles=4 invalid_commands=6 stale_choice_fullscreen_visit=true muted_late_plays=0 camera_model_vibration_retained=true\n");
        } finally {
            read(runner,()->{sound(host).setChecked(false);set(host,"toneWorker",original);return true;});
            controlled.shutdown();if(!controlled.awaitIdle(3000))throw new AssertionError("Controlled sound worker did not close");
            broken.shutdown();if(!broken.awaitIdle(3000))throw new AssertionError("Broken PiP audio worker did not close");
            info.flags=flags;automation.setServiceInfo(info);
            if(creates.get()!=closes.get() || mainThread.get())throw new AssertionError("Controlled audio resource ownership mismatch");
            SupportProbe.write(runner,"pip-sound-controls.txt",trace+"audio_creates="+creates.get()+" audio_closes="+closes.get()+"\n");
            report.append("PIP_SOUND_TRACE_BEGIN\n").append(trace).append("audio_creates=").append(creates.get())
                .append(" audio_closes=").append(closes.get()).append("\nPIP_SOUND_TRACE_END\n");
        }
    }

    private static RemoteAction soundAction(PreviewActivity host,boolean enabled)throws Exception {
        List<RemoteAction> actions=((PictureInPictureParams)field(host,"submittedPipParams")).getActions();
        if(actions.size()!=3 || !actions.get(0).getTitle().toString().equals(host.getString(R.string.preview_stop))
            || !actions.get(1).getTitle().toString().equals(host.getString(R.string.pip_stop_detection)))throw new AssertionError("Sound control displaced stop actions");
        RemoteAction action=actions.get(2);
        if(!action.isEnabled() || !action.getTitle().toString().equals(host.getString(enabled?R.string.pip_sound_off:R.string.pip_sound_on))
            || !action.getContentDescription().toString().equals(host.getString(enabled?R.string.pip_sound_off_description:R.string.pip_sound_on_description)))
            throw new AssertionError("Sound action does not describe the next explicit choice");
        return action;
    }
    private static void tapMenuAction(Instrumentation runner,PreviewActivity host,boolean enabled,String screenshot,StringBuilder trace)throws Exception {
        String description=host.getString(enabled?R.string.pip_sound_off_description:R.string.pip_sound_on_description);
        String title=host.getString(enabled?R.string.pip_sound_off:R.string.pip_sound_on);
        closeMenu(runner,host);
        Rect pip=stableBounds(()->pipBounds(runner,host),"actual PiP screen bounds");
        trace.append("OPEN ").append(title).append(" pip_screen=").append(pip).append('\n');
        touch(runner,pip.centerX(),pip.centerY());
        Rect target=stableBounds(()->findAction(runner,description,title),"visible SystemUI "+title);
        if(target==null)throw new AssertionError("Timed out: visible SystemUI "+title);
        if(screenshot!=null)PipProbe.screenshot(runner,screenshot);
        Rect rect=target;trace.append("TOUCH ").append(title).append(" target=").append(rect).append('\n');
        touch(runner,rect.centerX(),rect.centerY());
    }
    private static Rect findAction(Instrumentation runner,String description,String title) {
        if(Build.VERSION.SDK_INT>=33)runner.getUiAutomation().clearCache();
        for(AccessibilityWindowInfo window:runner.getUiAutomation().getWindows()) {
            AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            try{Rect found=findNode(root,description,title);if(found!=null)return found;}finally{root.recycle();}
        }
        return null;
    }
    private static Rect findNode(AccessibilityNodeInfo node,String description,String title) {
        if(node.isVisibleToUser() && node.isEnabled() && (description.contentEquals(node.getContentDescription()==null?"":node.getContentDescription())
            || title.contentEquals(node.getText()==null?"":node.getText()))) {
            if(!node.refresh() || !node.isVisibleToUser() || !node.isEnabled()
                || !(description.contentEquals(node.getContentDescription()==null?"":node.getContentDescription())
                    || title.contentEquals(node.getText()==null?"":node.getText())))return null;
            Rect rect=new Rect();node.getBoundsInScreen(rect);if(!rect.isEmpty())return rect;
        }
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;
            try{Rect found=findNode(child,description,title);if(found!=null)return found;}finally{child.recycle();}
        }
        return null;
    }
    private static void closeMenu(Instrumentation runner,PreviewActivity host)throws Exception {
        if(!menuVisible(runner,host))return;
        long at=SystemClock.uptimeMillis();for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP})
            if(!runner.getUiAutomation().injectInputEvent(new KeyEvent(at,SystemClock.uptimeMillis(),action,KeyEvent.KEYCODE_BACK,0),true))throw new AssertionError("SystemUI menu close was rejected");
        long end=SystemClock.elapsedRealtime()+6000;
        while(SystemClock.elapsedRealtime()<end){if(!menuVisible(runner,host))return;Thread.sleep(25);}
        throw new AssertionError("SystemUI menu did not close");
    }
    private static boolean menuVisible(Instrumentation runner,PreviewActivity host) {
        return findAction(runner,host.getString(R.string.pip_sound_on_description),host.getString(R.string.pip_sound_on))!=null
            || findAction(runner,host.getString(R.string.pip_sound_off_description),host.getString(R.string.pip_sound_off))!=null;
    }
    private static Rect pipBounds(Instrumentation runner,PreviewActivity host) {
        if(Build.VERSION.SDK_INT>=33)runner.getUiAutomation().clearCache();
        for(AccessibilityWindowInfo window:runner.getUiAutomation().getWindows()) {
            if(!window.isInPictureInPictureMode())continue;
            AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            try {
                if(!host.getPackageName().contentEquals(root.getPackageName()==null?"":root.getPackageName()))continue;
                Rect rect=new Rect();window.getBoundsInScreen(rect);if(!rect.isEmpty())return rect;
            }finally{root.recycle();}
        }
        return null;
    }
    private static Rect stableBounds(Value<Rect> reader,String message)throws Exception {
        long end=SystemClock.elapsedRealtime()+6000,stableAt=0;Rect previous=null;
        while(SystemClock.elapsedRealtime()<end) {
            Rect next=reader.get();long now=SystemClock.elapsedRealtime();
            if(next!=null && next.equals(previous)){if(now-stableAt>=150)return next;}
            else{previous=next;stableAt=now;}
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out: "+message+" last="+previous);
    }
    private static void touch(Instrumentation runner,int x,int y) {
        long at=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
            MotionEvent event=MotionEvent.obtain(at,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try{if(!runner.getUiAutomation().injectInputEvent(event,true))throw new AssertionError("SystemUI touch rejected");}finally{event.recycle();}
            if(action==MotionEvent.ACTION_DOWN)SystemClock.sleep(40);
        }
    }
    private static CheckBox sound(PreviewActivity host){return host.findViewById(R.id.preview_sound);}
    private static CheckBox vibration(PreviewActivity host){return host.findViewById(R.id.preview_vibration);}
    private static SessionState state(PreviewActivity host)throws Exception{return (SessionState)field(host,"state");}
    private interface Value<T>{T get()throws Exception;}
    private static <T>T read(Instrumentation runner,Value<T> fn) {
        AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runner.runOnMainSync(()->{try{value.set(fn.get());}catch(Throwable t){error.set(t);}});
        if(error.get()!=null)throw new AssertionError(error.get());return value.get();
    }
    private static void await(Instrumentation runner,Value<Boolean> fn,String message)throws Exception {
        long end=SystemClock.elapsedRealtime()+6000;while(SystemClock.elapsedRealtime()<end){if(read(runner,fn))return;Thread.sleep(25);}throw new AssertionError("Timed out: "+message);
    }
    private static Object field(Object host,String name)throws Exception{Field f=host.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(host);}
    private static void set(Object host,String name,Object value)throws Exception{Field f=host.getClass().getDeclaredField(name);f.setAccessible(true);f.set(host,value);}
}
