package com.cyanrbt.walkbuddy;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.Intent;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.CheckBox;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.*;
import org.json.JSONArray;
import org.json.JSONObject;

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
        Evidence evidence=new Evidence(runner,host);
        Throwable primaryFailure=null;
        Object camera=read(runner,()->field(host,"camera")),texture=read(runner,()->field(host,"texture"));
        InferenceWorker.Ticket ticket=read(runner,()->(InferenceWorker.Ticket)field(host,"analysisTicket"));
        RiskHaptics tactile=read(runner,()->(RiskHaptics)field(host,"haptics"));boolean vibration=read(runner,()->vibration(host).isChecked());
        try {
            evidence.state("initial");
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
            evidence.state("invalid_commands_sent");
            tapMenuAction(runner,host,false,"pip-sound-menu-off.png",trace,evidence);
            await(runner,()->sound(host).isChecked(),"SystemUI enables only prompt tones");
            JSONObject enabledSnapshot=evidence.state("first_enable_observed");
            TonePlayer.Session session=read(runner,()->(TonePlayer.Session)field(host,"activeTone"));
            RemoteAction oldDisable=read(runner,()->soundAction(host,true));
            tapMenuAction(runner,host,true,"pip-sound-menu-on.png",trace,evidence);
            await(runner,()->!sound(host).isChecked(),"SystemUI disables prompt tones");
            JSONObject mutedSnapshot=evidence.state("first_mute_observed");
            if(!enabledSnapshot.getBoolean("checked") || mutedSnapshot.getBoolean("checked")
                || enabledSnapshot.getLong("revision")>=mutedSnapshot.getLong("revision"))
                throw new AssertionError("Frozen enabled state did not survive the later real mute");
            if(!controlled.awaitIdle(3000))throw new AssertionError("Muted audio did not drain");
            read(runner,()->{if(session==null || !session.cancelled || field(host,"activeTone")!=null)throw new AssertionError("Mute retained the old audio session");return true;});
            long revision=read(runner,()->(long)field(host,"soundRevision"));
            PipProbe.sendAction(runner,oldEnable);PipProbe.sendAction(runner,oldDisable);
            read(runner,()->{if(sound(host).isChecked() || (long)field(host,"soundRevision")!=revision)throw new AssertionError("Old choice action replayed");return true;});
            tapMenuAction(runner,host,false,null,trace,evidence);
            await(runner,()->sound(host).isChecked(),"fresh SystemUI action re-enables tones");
            evidence.state("second_enable_observed");
            PipProbe.sendAction(runner,oldDisable);
            read(runner,()->{if(!sound(host).isChecked())throw new AssertionError("Old mute changed a newer choice");return true;});
            tapMenuAction(runner,host,true,null,trace,evidence);
            await(runner,()->!sound(host).isChecked(),"second SystemUI mute");
            evidence.state("second_mute_observed");
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
            evidence.verifyCommands();
            checkFailureFreeze(runner,host,controlled);
            evidence.finish("passed",null);
            report.append("PIP_SOUND_DIAGNOSTICS_PASS received_commands=17 invalid_guard_reasons=6 frozen_choice_retained=true native_ui_toggles=4 injected_failure_before_cleanup=true\n");
            report.append("PIP_SOUND_FAILURE_MENU_PASS sound_off=true next_action=enable stale_retry_ignored=true detection_camera_vibration_retained=true\n");
            trace.append("guarded_invalid_commands=6 real_ui_toggles=4 stale_choice_fullscreen_visit=true muted_late_plays=0 camera_model_vibration_retained=true\n");
            report.append("PIP_SOUND_CONTROLS_PASS ui_toggles=4 invalid_commands=6 stale_choice_fullscreen_visit=true muted_late_plays=0 camera_model_vibration_retained=true\n");
        } catch(Exception|AssertionError error) {
            primaryFailure=error;
            // Freeze the original selection and menu before finally cancels audio or restores the worker.
            evidence.failure("failed",error);
            throw error;
        } finally { try {
            read(runner,()->{sound(host).setChecked(false);set(host,"toneWorker",original);return true;});
            controlled.shutdown();if(!controlled.awaitIdle(3000))throw new AssertionError("Controlled sound worker did not close");
            broken.shutdown();if(!broken.awaitIdle(3000))throw new AssertionError("Broken PiP audio worker did not close");
            info.flags=flags;automation.setServiceInfo(info);
            if(creates.get()!=closes.get() || mainThread.get())throw new AssertionError("Controlled audio resource ownership mismatch");
            SupportProbe.write(runner,"pip-sound-controls.txt",trace+"audio_creates="+creates.get()+" audio_closes="+closes.get()+"\n");
            report.append("PIP_SOUND_TRACE_BEGIN\n").append(trace).append("audio_creates=").append(creates.get())
                .append(" audio_closes=").append(closes.get()).append("\nPIP_SOUND_TRACE_END\n");
        } catch(Exception|AssertionError cleanup) {
            if(primaryFailure==null)throw cleanup;
            primaryFailure.addSuppressed(cleanup);
        }}
    }

    private static void checkFailureFreeze(Instrumentation runner,PreviewActivity host,TonePlayer controlled)throws Exception {
        TonePlayer worker=read(runner,()->(TonePlayer)field(host,"toneWorker"));
        Evidence injected=new Evidence(runner,host);
        try {
            read(runner,()->{set(host,"toneWorker",controlled);sound(host).setChecked(true);return true;});
            try{throw new AssertionError("Injected diagnostic capture fixture; not a natural PiP failure");}
            catch(AssertionError expected) {
                injected.failure("injected_failure_fixture",expected);
                if(expected.getSuppressed().length!=0)throw new AssertionError("Injected failure evidence capture failed",expected);
            }
        } finally {read(runner,()->{sound(host).setChecked(false);set(host,"toneWorker",worker);return true;});}
        java.io.File file=new java.io.File(runner.getTargetContext().getCacheDir(),"ui-probe/"+injected.prefix+"-before-cleanup.json");
        JSONObject saved=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));
        if(!saved.getString("outcome").equals("injected_failure_fixture") || !saved.getJSONObject("before_cleanup").getBoolean("checked")
            || read(runner,()->sound(host).isChecked()) || !file.isFile())throw new AssertionError("Cleanup overwrote the frozen failure choice");
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
    private static void tapMenuAction(Instrumentation runner,PreviewActivity host,boolean enabled,String screenshot,StringBuilder trace,Evidence evidence)throws Exception {
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
        evidence.state("before_touch_"+enabled);
        JSONObject input=new JSONObject().put("title",title).put("requested",!enabled).put("target",rect.toShortString())
            .put("windows",evidence.windows());
        evidence.touches.put(input);
        touch(runner,rect.centerX(),rect.centerY(),input);
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
        touch(runner,x,y,null);
    }
    private static void touch(Instrumentation runner,int x,int y,JSONObject record) {
        long at=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
            MotionEvent event=MotionEvent.obtain(at,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try{
                boolean accepted=runner.getUiAutomation().injectInputEvent(event,true);
                if(record!=null)try{record.put(action==MotionEvent.ACTION_DOWN?"down_injected":"up_injected",accepted)
                    .put(action==MotionEvent.ACTION_DOWN?"down_uptime_ms":"up_uptime_ms",event.getEventTime());}
                    catch(org.json.JSONException error){throw new AssertionError(error);}
                if(!accepted)throw new AssertionError("SystemUI touch rejected");
            }finally{event.recycle();}
            if(action==MotionEvent.ACTION_DOWN)SystemClock.sleep(40);
        }
    }

    /** Test-process snapshots and command logs; never retain Views, workers or mutable state objects. */
    private static final class Evidence {
        final Instrumentation runner;final PreviewActivity host;
        final long started=SystemClock.elapsedRealtime();
        final String prefix="pip-sound-"+android.os.Process.myPid()+"-"+started;
        final JSONArray states=new JSONArray(),touches=new JSONArray();
        JSONArray commands=new JSONArray();
        Evidence(Instrumentation runner,PreviewActivity host){this.runner=runner;this.host=host;}
        JSONObject state(String stage) {
            JSONObject value=read(runner,()->{
                long now=SystemClock.elapsedRealtime();SessionState state=PipSoundProbe.state(host);
                JSONArray actions=new JSONArray();PictureInPictureParams params=(PictureInPictureParams)field(host,"submittedPipParams");
                if(params!=null)for(RemoteAction action:params.getActions())actions.put(new JSONObject()
                    .put("title",action.getTitle()).put("description",action.getContentDescription()).put("enabled",action.isEnabled()));
                return new JSONObject().put("stage",stage).put("elapsed_ms",now).put("owner",field(host,"pipOwner"))
                    .put("generation",state.generation).put("revision",field(host,"soundRevision"))
                    .put("visit",field(host,"pipSoundVisit")).put("submitted_revision",field(host,"submittedSoundRevision"))
                    .put("submitted_visit",field(host,"submittedSoundVisit")).put("phase",state.phase.name())
                    .put("pip",host.isInPictureInPictureMode()).put("recognition",state.recognition)
                    .put("enabled",sound(host).isEnabled()).put("checked",sound(host).isChecked())
                    .put("capture_age_ms",SessionDiagnostics.age(now,state.captureAt))
                    .put("display_age_ms",SessionDiagnostics.age(now,state.displayAt))
                    .put("analysis_age_ms",SessionDiagnostics.age(now,state.analysisAt)).put("active_tone",field(host,"activeTone")!=null)
                    .put("submitted_actions",actions);
            });
            states.put(value);return value;
        }
        JSONArray windows()throws Exception {
            JSONArray result=new JSONArray();
            if(Build.VERSION.SDK_INT>=33)runner.getUiAutomation().clearCache();
            for(AccessibilityWindowInfo window:runner.getUiAutomation().getWindows()) {
                AccessibilityNodeInfo root=window.getRoot();Rect bounds=new Rect();window.getBoundsInScreen(bounds);
                try {
                    JSONArray targets=new JSONArray();
                    if(root!=null)for(boolean enabled:new boolean[]{false,true}) {
                        String title=host.getString(enabled?R.string.pip_sound_off:R.string.pip_sound_on);
                        Rect target=findNode(root,host.getString(enabled?R.string.pip_sound_off_description:R.string.pip_sound_on_description),title);
                        if(target!=null)targets.put(new JSONObject().put("title",title).put("bounds",target.toShortString()));
                    }
                    result.put(new JSONObject().put("id",window.getId()).put("type",window.getType())
                        .put("active",window.isActive()).put("focused",window.isFocused()).put("pip",window.isInPictureInPictureMode())
                        .put("bounds",bounds.toShortString()).put("package",root==null?JSONObject.NULL:root.getPackageName()).put("targets",targets));
                }finally{if(root!=null)root.recycle();window.recycle();}
            }
            return result;
        }
        void commands()throws Exception {
            String text;
            try(ParcelFileDescriptor pipe=runner.getUiAutomation().executeShellCommand(
                "logcat -d -v raw --pid="+android.os.Process.myPid()+" -s WalkBuddyPipSound:I '*:S'");
                FileInputStream stream=new FileInputStream(pipe.getFileDescriptor())) {
                text=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            }
            JSONArray captured=new JSONArray();
            java.util.regex.Pattern pattern=java.util.regex.Pattern.compile("command elapsedMs=(\\d+).*?result=(\\w+)");
            for(String line:text.split("\\n")) {
                java.util.regex.Matcher match=pattern.matcher(line);
                if(match.find() && Long.parseLong(match.group(1))>=started)captured.put(new JSONObject()
                    .put("elapsed_ms",Long.parseLong(match.group(1))).put("result",match.group(2)).put("raw",line));
            }
            commands=captured;
        }
        void verifyCommands()throws Exception {
            commands();
            String[] invalid={"rejected_owner","rejected_generation","rejected_revision","rejected_visit","rejected_missing_enabled","rejected_revision"};
            if(commands.length()!=17)throw new AssertionError("Expected 17 received sound commands, recorded "+commands.length());
            for(int i=0;i<invalid.length;i++)if(!invalid[i].equals(commands.getJSONObject(i).getString("result")))
                throw new AssertionError("Wrong actual receiver guard diagnostic at "+i+": "+commands.getJSONObject(i));
            for(int i:new int[]{6,7,10,12,15})if(!"applied".equals(commands.getJSONObject(i).getString("result")))
                throw new AssertionError("Expected actual sound command applied at "+i);
        }
        void finish(String outcome,Throwable error)throws Exception {
            JSONObject frozen=state("before_cleanup");
            JSONObject result=new JSONObject().put("outcome",outcome).put("pid",android.os.Process.myPid()).put("started_ms",started)
                .put("failure",error==null?JSONObject.NULL:error.toString()).put("before_cleanup",frozen)
                .put("states",states).put("touches",touches)
                .put("scope","Emulator snapshot before audio cleanup. Input injection acceptance alone does not prove command receipt. No physical audibility claim.");
            // Persist the primary state even if a later logcat or accessibility query fails.
            SupportProbe.write(runner,prefix+"-before-cleanup.json",result.toString(2)+"\n");
            try{commands();result.put("commands",commands);}catch(Exception|AssertionError capture){result.put("command_capture_error",capture.toString());}
            try{result.put("windows",windows());}catch(Exception|AssertionError capture){result.put("window_capture_error",capture.toString());}
            SupportProbe.write(runner,prefix+"-before-cleanup.json",result.toString(2)+"\n");
        }
        void failure(String outcome,Throwable error) {
            try{finish(outcome,error);}catch(Exception|AssertionError capture){error.addSuppressed(capture);}
            try{PipProbe.screenshot(runner,prefix+"-before-cleanup.png");}
            catch(Exception|AssertionError capture){error.addSuppressed(capture);}
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
