package com.cyanrbt.walkbuddy;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Actual Camera2 and real touch toggles; a deliberate failure exercises recovery expansion. */
final class ViewfinderProbe {
    static void run(Instrumentation runner,Activity launcher,PreviewActivity host,StringBuilder report) throws Exception {
        await(runner,()->live(host) && host.hasWindowFocus(),"viewfinder initial live");
        Snapshot before=read(runner,()->new Snapshot(host));
        long area=read(runner,()->area(host.findViewById(R.id.preview_frame)));
        double image=read(runner,()->imageArea(host));
        JSONObject normal=read(runner,()->geometry(host));
        PipProbe.tap(runner,host,R.id.viewfinder_toggle);
        await(runner,()->enlarged(host) && area(host.findViewById(R.id.preview_frame))>area && imageArea(host)>image,"larger actual camera pixels");
        read(runner,()->{before.unchanged(host);assertEnlarged(host);return true;});
        long at=SystemClock.elapsedRealtime();
        await(runner,()->live(host) && state(host).captureAt>at+250 && state(host).displayAt>at+250,"frames after enlargement");
        PipProbe.screenshot(runner,"viewfinder-actual-enlarged.png");
        JSONObject large=read(runner,()->geometry(host));
        SupportProbe.write(runner,"viewfinder-actual-geometry.json",new JSONObject().put("normal",normal).put("enlarged",large)
            .put("scope","Actual emulated Camera2 window and production display geometry; no device FPS/power claim").toString(2));
        PipProbe.tap(runner,host,R.id.enter_pip);
        await(runner,host::isInPictureInPictureMode,"enlarged explicit PiP");
        read(runner,()->{
            before.unchanged(host);
            if(!(boolean)field(host,"enlargedPreview"))throw new AssertionError("PiP lost the user's viewport choice");
            return true;
        });
        PipProbe.tap(runner,launcher,R.id.start);
        await(runner,()->!host.isInPictureInPictureMode() && host.hasWindowFocus() && enlarged(host),"enlarged PiP return");
        read(runner,()->{before.unchanged(host);assertEnlarged(host);return true;});
        PipProbe.tap(runner,host,R.id.viewfinder_toggle);
        await(runner,()->live(host) && host.findViewById(R.id.preview_controls).isShown()
            && area(host.findViewById(R.id.preview_frame))==area && imageArea(host)==image,"settings and viewport restored");
        read(runner,()->{before.unchanged(host);return true;});
        PipProbe.screenshot(runner,"viewfinder-actual-controls.png");
        PipProbe.tap(runner,host,R.id.viewfinder_toggle);
        await(runner,()->enlarged(host),"enlarged before controlled failure");
        read(runner,()->{invoke(host,"fail",new Class<?>[]{String.class},"Controlled enlarged-view recovery check");return true;});
        await(runner,()->state(host).phase==SessionState.Phase.UNAVAILABLE && host.findViewById(R.id.preview_controls).isShown()
            && RecoveryPresentationProbe.laidOut((View)field(host,"root")),"failure expands recovery");
        read(runner,()->{
            if((boolean)field(host,"enlargedPreview") || host.findViewById(R.id.viewfinder_toggle).isEnabled())
                throw new AssertionError("Stopped preview retained enlarged controls");
            for(String name:new String[]{"camera","session","surface","depthCamera","brightnessReadback","analysisTicket","pendingReadback"})
                if(field(host,name)!=null)throw new AssertionError("Controlled failure retained "+name);
            RecoveryPresentationProbe.full((View)field(host,"root"),RecoveryGuide.resolve(RecoveryGuide.Reason.GENERAL,true));
            return true;
        });
        PipProbe.tap(runner,host,R.id.restart_preview);
        await(runner,()->live(host) && host.findViewById(R.id.preview_controls).isShown(),"explicit restart with settings");
        report.append("VIEWFINDER_ACTIVITY_PASS cases=4 native_touch=true camera_retained=true advancing_frames=true pip_choice_restored=true recovery_expands=true\n");
    }
    static void detection(Instrumentation runner,PreviewActivity host,StringBuilder report) throws Exception {
        Snapshot before=read(runner,()->new Snapshot(host));
        Object model=read(runner,()->field(field(host,"worker"),"detector"));
        if(!before.recognition || model==null)throw new AssertionError("Enlarged detection requires the real ready model");
        PipProbe.tap(runner,host,R.id.viewfinder_toggle);
        await(runner,()->enlarged(host),"enlarged model view");
        long at=SystemClock.elapsedRealtime();
        await(runner,()->live(host) && state(host).recognition && (boolean)field(host,"analysisHasResult")
            && state(host).analysisAt>at+250,"actual model results in enlarged view");
        read(runner,()->{
            before.unchanged(host);
            if(field(field(host,"worker"),"detector")!=model || host.findViewById(R.id.detection_status).getVisibility()!=View.GONE)
                throw new AssertionError("Enlargement replaced model or claimed detection off");
            return true;
        });
        PipProbe.tap(runner,host,R.id.viewfinder_toggle);
        await(runner,()->host.findViewById(R.id.preview_controls).isShown() && live(host),"model settings restored");
        read(runner,()->{before.unchanged(host);return true;});
        report.append("VIEWFINDER_DETECTION_PASS real_model=true choices_retained=true results_advance=true\n");
    }
    private static void assertEnlarged(PreviewActivity host) {
        View button=host.findViewById(R.id.viewfinder_toggle);
        if(!button.isShown() || !button.isEnabled() || !button.isSelected() || button.getWidth()<48 || button.getHeight()<48
            || !host.getText(R.string.viewfinder_controls).equals(button.getContentDescription()))
            throw new AssertionError("Enlarged view lost its accessible restore action");
        AccessibilityNodeInfo node=button.createAccessibilityNodeInfo();
        if(!node.isClickable() || !node.isEnabled() || !node.isSelected())throw new AssertionError("Restore button has wrong native accessibility state");
        for(int id:new int[]{R.id.preview_header,R.id.preview_stop,R.id.preview_actions,R.id.enter_pip})
            if(!host.findViewById(id).isShown())throw new AssertionError("Enlargement hid essential action "+id);
        if(!state(host).recognition && (!host.findViewById(R.id.detection_status).isShown()
            || !host.getText(R.string.detection_off_enlarged).equals(((TextView)host.findViewById(R.id.detection_status)).getText())))
            throw new AssertionError("Enlarged default preview hid detection-off state");
    }
    private static final class Snapshot {
        final Object camera,texture,brightness;final long generation,startedAt;final boolean recognition,sound,vibration;
        Snapshot(PreviewActivity host) {
            camera=field(host,"camera");texture=field(host,"texture");brightness=field(host,"brightnessReadback");
            generation=state(host).generation;startedAt=state(host).startedAt;recognition=state(host).recognition;
            sound=((CheckBox)host.findViewById(R.id.preview_sound)).isChecked();vibration=((CheckBox)host.findViewById(R.id.preview_vibration)).isChecked();
            if(camera==null || texture==null || brightness==null)throw new AssertionError("No actual Camera2 observation");
        }
        void unchanged(PreviewActivity host) {
            if(field(host,"camera")!=camera || field(host,"texture")!=texture || field(host,"brightnessReadback")!=brightness
                || state(host).generation!=generation || state(host).startedAt!=startedAt || state(host).recognition!=recognition
                || ((CheckBox)host.findViewById(R.id.preview_sound)).isChecked()!=sound
                || ((CheckBox)host.findViewById(R.id.preview_vibration)).isChecked()!=vibration)
                throw new AssertionError("Viewport presentation restarted camera or changed choices");
        }
    }
    private static long area(View view){return (long)view.getWidth()*view.getHeight();}
    private static double imageArea(PreviewActivity host) {
        PreviewGeometry geometry=(PreviewGeometry)field(host,"previewGeometry");
        return geometry==null?0:(double)geometry.width*geometry.height;
    }
    private static JSONObject geometry(PreviewActivity host) throws Exception {
        PreviewGeometry geometry=(PreviewGeometry)field(host,"previewGeometry");
        View frame=host.findViewById(R.id.preview_frame);
        return new JSONObject().put("frameWidth",frame.getWidth()).put("frameHeight",frame.getHeight())
            .put("imageWidth",geometry.width).put("imageHeight",geometry.height).put("imageArea",imageArea(host));
    }
    private static boolean enlarged(PreviewActivity host){return live(host) && !host.isInPictureInPictureMode() && host.findViewById(R.id.preview_controls).getVisibility()==View.GONE;}
    private static boolean live(PreviewActivity host){return state(host).phase==SessionState.Phase.LIVE && state(host).fresh(SystemClock.elapsedRealtime());}
    private static SessionState state(PreviewActivity host){return (SessionState)field(host,"state");}
    private static Object field(Object source,String name) {try{Field f=source.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(source);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void invoke(Object source,String name,Class<?>[] types,Object... args) {try{Method m=source.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(source,args);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static <T>T read(Instrumentation runner,Callable<T> call) {
        AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runner.runOnMainSync(()->{try{value.set(call.call());}catch(Throwable e){error.set(e);}});
        if(error.get()!=null)throw new AssertionError(error.get());return value.get();
    }
    private static void await(Instrumentation runner,BooleanSupplier ready,String label) {
        long until=SystemClock.elapsedRealtime()+10000;
        do{if(read(runner,ready::getAsBoolean))return;SystemClock.sleep(30);}while(SystemClock.elapsedRealtime()<until);
        throw new AssertionError("Timed out: "+label+"; "+PreviewActivity.report());
    }
}
