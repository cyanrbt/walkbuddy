package com.cyanrbt.walkbuddy;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real Camera2, real clipboard and native Button touches; snapshot data does not become an incident. */
final class ObservationInfoProbe {
    static void run(Instrumentation runner,PreviewActivity host,StringBuilder report)throws Exception {
        JSONArray copies=new JSONArray();
        ScrollView panel=host.findViewById(R.id.preview_controls);int originalScroll=read(runner,panel::getScrollY);
        boolean originallyRecognizing=read(runner,()->state(host).recognition);
        if(originallyRecognizing)throw new AssertionError("Observation copy probe requires default preview-only start");
        Throwable primaryFailure=null;
        try {
            copy(runner,host,false,copies);
            read(runner,()->{panel.scrollTo(0,0);return true;});
            PipProbe.tap(runner,host,R.id.preview_recognition);
            await(runner,()->live(host) && state(host).recognition && (boolean)field(host,"analysisReady")
                && (boolean)field(host,"analysisHasResult") && field(host,"analysisTicket")!=null,"actual model before observation copy");
            copy(runner,host,true,copies);
            read(runner,()->{
                Bundle saved=new Bundle();String key=(String)field(host,"PROBLEM_INFO_STATE");
                saved.putString(key,copies.getJSONObject(1).getString("copied_text"));
                Method method=PreviewActivity.class.getDeclaredMethod("onSaveInstanceState",Bundle.class);method.setAccessible(true);method.invoke(host,saved);
                if(saved.containsKey(key) || diagnostics(host).available())throw new AssertionError("Current copy became saved incident evidence");
                return true;
            });
            JSONObject incident=copyLiveIncident(runner,host);
            SupportProbe.write(runner,"observation-copy-actual.json",new JSONObject().put("copies",copies)
                .put("live_incident_copy",incident)
                .put("saved_bundle_excludes_current_report",true).put("current_copies_create_incidents",false)
                .put("scope","Actual emulator Camera2/model, native copy Button touches and plain-text clipboard; actual save callback Bundle checked, not a new healthy-page recreation or physical acceptance").toString(2)+"\n");
            report.append("OBSERVATION_COPY_PASS cases=4 native_touch=true plain_text_clipboard=true camera_model_retained=true advancing_frames=true saved_bundle_excludes_current=true first_incident_unchanged=true\n");
        } catch(Exception|AssertionError error) {
            primaryFailure=error;
            try {
                JSONObject frozen=read(runner,()->{
                    ClipData clip=host.getSystemService(ClipboardManager.class).getPrimaryClip();
                    return new JSONObject().put("phase",state(host).phase.name()).put("recognition",state(host).recognition)
                        .put("incident_available",diagnostics(host).available()).put("copy_label",((TextView)host.findViewById(R.id.copy_problem_info)).getText())
                        .put("clipboard_text",clip==null?JSONObject.NULL:String.valueOf(clip.getItemAt(0).getText()));
                });
                SupportProbe.write(runner,"observation-copy-failure-before-cleanup.json",new JSONObject().put("failure",error.toString())
                    .put("completed_copies",copies).put("before_cleanup",frozen).toString(2)+"\n");
                PipProbe.screenshot(runner,"observation-copy-failure-before-cleanup.png");
            } catch(Exception|AssertionError capture){error.addSuppressed(capture);}
            throw error;
        } finally { try {
            read(runner,()->{
                // An explicit detection retry retires the injected incident before restoring preview-only state.
                if(diagnostics(host).detection()!=null && diagnostics(host).preview()==null)
                    ((CheckBox)host.findViewById(R.id.preview_recognition)).setChecked(true);
                if(((CheckBox)host.findViewById(R.id.preview_recognition)).isChecked()!=originallyRecognizing)
                    ((CheckBox)host.findViewById(R.id.preview_recognition)).setChecked(originallyRecognizing);
                ClipboardManager clipboard=host.getSystemService(ClipboardManager.class);if(clipboard!=null)clipboard.clearPrimaryClip();
                panel.scrollTo(0,originalScroll);return true;
            });
        } catch(Exception|AssertionError cleanup){if(primaryFailure==null)throw cleanup;primaryFailure.addSuppressed(cleanup);}}
    }
    private static JSONObject copyLiveIncident(Instrumentation runner,PreviewActivity host)throws Exception {
        Object camera=read(runner,()->field(host,"camera"));long generation=read(runner,()->state(host).generation);
        String frozen=read(runner,()->{
            Method pause=PreviewActivity.class.getDeclaredMethod("pauseDetection",int.class);pause.setAccessible(true);
            pause.invoke(host,R.string.info_detection_failed);
            if(!live(host) || state(host).recognition || !diagnostics(host).available())throw new AssertionError("Controlled detection error did not retain current camera");
            return diagnostics(host).report(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,Build.MANUFACTURER,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT);
        });
        if(!frozen.startsWith("WalkBuddy 问题信息\n") || !frozen.contains("分类：DETECTION_ERROR") || !frozen.contains("检测：开；声音：关"))
            throw new AssertionError("Controlled detection incident did not freeze the preceding active choices");
        await(runner,()->((TextView)host.findViewById(R.id.copy_problem_info)).getText().toString().equals(host.getString(R.string.copy_problem_info)),"incident copy role while camera LIVE");
        read(runner,()->{
            host.getSystemService(ClipboardManager.class).clearPrimaryClip();View copy=host.findViewById(R.id.copy_problem_info);
            ((ScrollView)host.findViewById(R.id.preview_controls)).requestChildFocus(copy,copy);return true;
        });
        PipProbe.screenshot(runner,"observation-copy-actual-injected-detection-error.png");
        long at=SystemClock.elapsedRealtime();PipProbe.tap(runner,host,R.id.copy_problem_info);
        read(runner,()->{
            ClipData clip=host.getSystemService(ClipboardManager.class).getPrimaryClip();
            if(clip==null || clip.getItemCount()!=1 || !clip.getDescription().hasMimeType("text/plain") || clip.getItemAt(0).getUri()!=null
                || clip.getItemAt(0).getIntent()!=null || !String.valueOf(clip.getItemAt(0).getText()).equals(frozen)
                || !host.getString(R.string.problem_info_title).contentEquals(clip.getDescription().getLabel()))
                throw new AssertionError("Live incident copy replaced the frozen first cause with current state");
            host.getSystemService(ClipboardManager.class).clearPrimaryClip();return true;
        });
        await(runner,()->live(host) && state(host).captureAt>at+200 && state(host).displayAt>at+200,"camera after copied live incident");
        if(read(runner,()->field(host,"camera")!=camera || state(host).generation!=generation || state(host).recognition
            || !diagnostics(host).report(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,Build.MANUFACTURER,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT).equals(frozen)))
            throw new AssertionError("Copy changed the live paused-detection observation or its first cause");
        return new JSONObject().put("origin","injected_detection_error").put("copied_text",frozen).put("camera_retained",true)
            .put("current_recognition",false).put("saved_first_recognition",true).put("generation",generation);
    }
    private static void copy(Instrumentation runner,PreviewActivity host,boolean detection,JSONArray copies)throws Exception {
        await(runner,()->live(host) && host.hasWindowFocus() && !diagnostics(host).available()
            && host.findViewById(R.id.copy_problem_info).getVisibility()==View.VISIBLE
            && ((TextView)host.findViewById(R.id.copy_problem_info)).getText().toString().equals(host.getString(R.string.copy_observation_info)),"current copy action rendered");
        Object camera=read(runner,()->field(host,"camera")),texture=read(runner,()->field(host,"texture"));
        Object brightness=read(runner,()->field(host,"brightnessReadback")),ticket=read(runner,()->field(host,"analysisTicket"));
        long generation=read(runner,()->state(host).generation);
        boolean vibration=read(runner,()->((CheckBox)host.findViewById(R.id.preview_vibration)).isChecked());
        read(runner,()->{
            host.getSystemService(ClipboardManager.class).clearPrimaryClip();
            View copy=host.findViewById(R.id.copy_problem_info);((ScrollView)host.findViewById(R.id.preview_controls)).requestChildFocus(copy,copy);return true;
        });
        PipProbe.awaitPreviewInputReady(runner,host,"current observation copy");
        String mode=detection?"detection":"preview";
        PipProbe.screenshot(runner,"observation-copy-actual-"+mode+".png");
        long at=SystemClock.elapsedRealtime();
        PipProbe.tap(runner,host,R.id.copy_problem_info);
        String copied=read(runner,()->{
            ClipData clip=host.getSystemService(ClipboardManager.class).getPrimaryClip();
            if(clip==null || clip.getItemCount()!=1 || !clip.getDescription().hasMimeType("text/plain")
                || clip.getItemAt(0).getUri()!=null || clip.getItemAt(0).getIntent()!=null
                || !host.getString(R.string.observation_info_title).contentEquals(clip.getDescription().getLabel()))
                throw new AssertionError("Current copy did not create one labelled plain-text item");
            String text=String.valueOf(clip.getItemAt(0).getText());
            if(!text.startsWith("WalkBuddy 观察信息\n版本："+BuildConfig.VERSION_NAME+" / "+BuildConfig.VERSION_CODE)
                || !text.contains("当前阶段：LIVE / 基础模式") || !text.contains("检测："+(detection?"开":"关")+"；声音：关；振动："+(vibration?"开":"关"))
                || text.contains("本次预览首次停止") || text.length()>4096
                || detection && !text.contains("模型就绪：是；已有结果：是"))
                throw new AssertionError("Copied observation does not match actual current mode");
            host.getSystemService(ClipboardManager.class).clearPrimaryClip();
            return text;
        });
        await(runner,()->live(host) && state(host).captureAt>at+200 && state(host).displayAt>at+200
            && (!detection || state(host).analysisAt>at+200),"frames after current copy");
        read(runner,()->{
            if(diagnostics(host).available() || state(host).generation!=generation || field(host,"camera")!=camera
                || field(host,"texture")!=texture || field(host,"brightnessReadback")!=brightness || field(host,"analysisTicket")!=ticket
                || state(host).recognition!=detection || ((CheckBox)host.findViewById(R.id.preview_sound)).isChecked()
                || ((CheckBox)host.findViewById(R.id.preview_vibration)).isChecked()!=vibration)
                throw new AssertionError("Copy changed the camera, model, choices or incident store");
            copies.put(new JSONObject().put("mode",mode).put("copied_text",copied).put("generation",generation)
                .put("copy_started_ms",at).put("after_capture_at",state(host).captureAt).put("after_display_at",state(host).displayAt)
                .put("after_analysis_at",state(host).analysisAt).put("camera_retained",true).put("model_ticket_retained",true)
                .put("vibration",vibration).put("incident_available",diagnostics(host).available()));return true;
        });
        SupportProbe.write(runner,"observation-copy-actual-"+mode+".txt",copied);
    }
    private static boolean live(PreviewActivity host){return state(host).phase==SessionState.Phase.LIVE && state(host).fresh(SystemClock.elapsedRealtime());}
    private static SessionDiagnostics diagnostics(PreviewActivity host){return (SessionDiagnostics)field(host,"diagnostics");}
    private static SessionState state(PreviewActivity host){return (SessionState)field(host,"state");}
    private static Object field(Object host,String name) {
        try{Field f=host.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(host);}catch(Exception error){throw new AssertionError(error);}
    }
    private static <T>T read(Instrumentation runner,Callable<T> action) {
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runner.runOnMainSync(()->{try{result.set(action.call());}catch(Throwable failure){error.set(failure);}});
        if(error.get()!=null)throw new AssertionError(error.get());return result.get();
    }
    private static void await(Instrumentation runner,BooleanSupplier condition,String label)throws Exception {
        long end=SystemClock.elapsedRealtime()+6000;
        while(SystemClock.elapsedRealtime()<end){if(read(runner,condition::getAsBoolean))return;Thread.sleep(25);}
        throw new AssertionError("Timed out: "+label);
    }
}
