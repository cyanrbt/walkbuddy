package com.cyanrbt.walkbuddy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.CheckBox;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;

/** Inflates shipped views on Android; tests layout/chrome only, never opens a camera or AR. */
final class LayoutProbe {
    static void run(Instrumentation runner) {
        Bundle result=new Bundle();StringBuilder report=new StringBuilder();
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("Layout probe is restricted to emulators");
            AtomicReference<Throwable> failure=new AtomicReference<>();
            runner.runOnMainSync(()->{try{check(runner.getTargetContext(),report);}catch(Throwable error){failure.set(error);}});
            if(failure.get()!=null)throw new AssertionError(failure.get());
            result.putString("stream",report+"LAYOUT_PROBE_PASS cases=30 states=120; recovery-presentations="
                +(RecoveryGuide.Reason.values().length*60)+"; initial toggles, full text, risk live region, fixed actions, camera space, scroll reach, compact/restore\n");
            runner.finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream",report+"LAYOUT_PROBE_FAIL: "+error);
            runner.finish(Activity.RESULT_CANCELED,result);
        }
    }

    private static void check(Context target,StringBuilder report) throws Exception {
        File output=new File(target.getCacheDir(),"ui-probe");
        if(!output.isDirectory() && !output.mkdirs())throw new IllegalStateException("Cannot create UI evidence directory");
        for(int[] size:new int[][]{{320,480},{360,640},{600,360},{640,360},{800,600}})
            for(float font:new float[]{1f,1.5f,2f})for(boolean night:new boolean[]{false,true}) {
                Configuration config=new Configuration(target.getResources().getConfiguration());
                config.fontScale=font;config.densityDpi=160;
                config.screenWidthDp=size[0];config.screenHeightDp=size[1];
                config.orientation=size[0]>size[1]?Configuration.ORIENTATION_LANDSCAPE:Configuration.ORIENTATION_PORTRAIT;
                config.uiMode=(config.uiMode&~Configuration.UI_MODE_NIGHT_MASK)
                    |(night?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
                Context context=new ContextThemeWrapper(target.createConfigurationContext(config),R.style.Theme_WalkBuddy);
                View root=LayoutInflater.from(context).inflate(R.layout.activity_preview,null);
                root.setPadding(0,24,0,24);
                root.findViewById(R.id.preview_cover).setVisibility(View.GONE);
                TextView placeholder=new TextView(context);
                placeholder.setText("取景区域（布局测试）");placeholder.setTextColor(Color.GRAY);placeholder.setGravity(Gravity.CENTER);
                ((FrameLayout)root.findViewById(R.id.preview_frame)).addView(placeholder,1,
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
                TextView title=root.findViewById(R.id.preview_title);
                title.setText(R.string.window_starting);measure(root,size[0],size[1]);textFits(root);
                if(title.getLineCount()!=1)throw new AssertionError("Starting badge wrapped");
                checkFeedback(root);
                ((TextView)root.findViewById(R.id.preview_title)).setText(R.string.window_live);
                String name=size[0]+"x"+size[1]+"-font"+Math.round(font*100)+(night?"-night":"-day");
                CheckBox detection=root.findViewById(R.id.preview_recognition),sound=root.findViewById(R.id.preview_sound);
                TextView info=root.findViewById(R.id.preview_info);
                String basic=confirmedMessage(Detection.fromModel("人",.8f,new float[]{.1f,.3f,.9f,.7f},320,320,0,0));
                String measured=confirmedMessage(new Detection("空间障碍",1,.3f,.1f,.7f,.9f,Detection.Risk.CAUTION,1.7f,null));
                for(int mode=0;mode<4;mode++) {
                    detection.setChecked(mode!=0);sound.setChecked(false);sound.setEnabled(mode!=0);
                    info.setText(mode==0?context.getString(R.string.info_recognition_off):mode==1
                        ?context.getString(R.string.info_recognition_preparing):mode==2?basic:measured);
                    root.findViewById(R.id.recent_alert).setVisibility(mode>=2?View.VISIBLE:View.GONE);
                    if(mode>=2)new RiskAnnouncer(root).update(info.getText().toString(),Detection.Risk.CAUTION,true,200);
                    PreviewChrome.depth(root,mode==3);
                    ((TextView)root.findViewById(R.id.depth_guide_hint)).setText(mode==3
                        ?"深度有效 · 实验测距提示":"基础模式 · 空间增强可选");
                    ((ScrollView)root.findViewById(R.id.preview_controls)).scrollTo(0,0);
                    measure(root,size[0],size[1]);
                    screenshot(root,new File(output,name+"-current.png"));
                    if(mode==0 || mode==2)screenshot(root,new File(output,name+(mode==0?"-basic":"-warning")+".png"));
                    initialVisible(root,detection);initialVisible(root,sound);
                    if(detection.getLineCount()!=1 || sound.getLineCount()!=1)throw new AssertionError("Toggle label wrapped: "+name);
                    if(mode<=1)initialVisible(root,info);
                    else initialQualifierVisible(root,info,mode==2?"未测距":"约 1.7 米");
                    if(root.findViewById(R.id.depth_steps).getVisibility()!=(mode==3?View.VISIBLE:View.GONE)
                        || root.findViewById(R.id.depth_status).getVisibility()!=(mode==3?View.VISIBLE:View.GONE))
                        throw new AssertionError("Optional depth details have wrong visibility");
                    textFits(root);
                    if(mode==3) {
                        ScrollView panel=root.findViewById(R.id.preview_controls);
                        for(int id:new int[]{R.id.guide_label_service,R.id.guide_label_ground,R.id.guide_label_depth,R.id.guide_label_alert})
                            if(((TextView)root.findViewById(id)).getLineCount()!=1)throw new AssertionError("Depth step label wrapped");
                        for(int id:new int[]{R.id.guide_step_service,R.id.guide_step_ground,R.id.guide_step_depth,R.id.guide_step_alert}) {
                            View step=root.findViewById(id);panel.requestChildFocus(step,step);Rect visible=new Rect();
                            if(!step.getLocalVisibleRect(visible) || visible.height()<48)throw new AssertionError("Depth step unreachable");
                        }
                    }
                }
                checkDetectionFailure(root,size[0],size[1],new File(output,name+"-detection-stopped.png"));
                measure(root,size[0],size[1]);
                checkDisabledButton(root.findViewById(R.id.enter_pip));
                View camera=root.findViewById(R.id.preview_frame),pane=root.findViewById(R.id.preview_content);
                if(camera.getWidth()<=0 || camera.getHeight()<pane.getHeight()/2)
                    throw new AssertionError("Camera space collapsed: "+camera.getWidth()+"x"+camera.getHeight());
                if(size[0]>=600 && camera.getWidth()>=pane.getWidth())throw new AssertionError("Wide preview did not use side panel");
                fullyVisible(root,root.findViewById(R.id.preview_stop));
                fullyVisible(root,root.findViewById(R.id.enter_pip));
                ScrollView scroll=root.findViewById(R.id.preview_controls);
                for(int id:new int[]{R.id.preview_recognition,R.id.preview_sound,R.id.enable_depth}) {
                    View control=root.findViewById(id);
                    scroll.requestChildFocus(control,control);
                    Rect visible=new Rect();
                    if(!control.getLocalVisibleRect(visible) || visible.height()<Math.min(48,control.getHeight()))
                        throw new AssertionError("Unreachable scroll control "+id+": "+visible);
                }
                scroll.scrollTo(0,0);
                detection.setChecked(false);sound.setChecked(false);
                PreviewChrome.showRestart(root,true);measure(root,size[0],size[1]);
                fullyVisible(root,root.findViewById(R.id.restart_preview));
                if(root.findViewById(R.id.enter_pip).getVisibility()!=View.GONE)throw new AssertionError("PiP action survived restart state");
                checkRecovery(root,size[0],size[1],new File(output,name+"-recovery.png"));
                PreviewChrome.compact(root,true,true);measure(root,180,240);
                if(camera.getWidth()!=180 || camera.getHeight()!=240)throw new AssertionError("PiP lost camera space to hidden controls");
                if(root.findViewById(R.id.pip_depth_status).getVisibility()!=View.VISIBLE)throw new AssertionError("Missing compact depth status");
                PreviewChrome.compact(root,false,false);PreviewChrome.showRestart(root,false);
                root.findViewById(R.id.preview_cover).setVisibility(View.GONE);
                PreviewRecovery.clear(root);
                root.setPadding(0,24,0,24);measure(root,size[0],size[1]);
                fullyVisible(root,root.findViewById(R.id.preview_stop));
                fullyVisible(root,root.findViewById(R.id.enter_pip));
                if(scroll.getVisibility()!=View.VISIBLE || scroll.getWidth()<=0)throw new AssertionError("Panel did not restore");
                if(root.findViewById(R.id.depth_steps).getVisibility()!=View.GONE)throw new AssertionError("Basic restore exposed depth steps");
                report.append("PASS ").append(name).append(" camera=").append(camera.getWidth()).append('x').append(camera.getHeight()).append('\n');
            }
    }

    private static String confirmedMessage(Detection detection) {
        AlertPolicy policy=new AlertPolicy();
        policy.update(java.util.Collections.singletonList(detection),100);
        return policy.update(java.util.Collections.singletonList(detection),600).message;
    }
    private static void checkDetectionFailure(View root,int width,int height,File output) throws Exception {
        TextView info=root.findViewById(R.id.preview_info),title=root.findViewById(R.id.preview_title);
        CharSequence previousInfo=info.getText(),previousTitle=title.getText();
        ScrollView controls=root.findViewById(R.id.preview_controls);int previousScroll=controls.getScrollY();
        controls.scrollTo(0,0);
        boolean depth=root.findViewById(R.id.depth_steps).getVisibility()==View.VISIBLE;
        int previousAlert=root.findViewById(R.id.recent_alert).getVisibility();
        root.findViewById(R.id.recent_alert).setVisibility(View.GONE);
        info.setText(R.string.info_detection_timeout);title.setText(R.string.window_preview_only);
        PreviewChrome.detectionStopped(root,true,false);measure(root,width,height);
        TextView warning=root.findViewById(R.id.detection_status);
        textFits(root);warningInsideCamera(root,warning);
        if(warning.getAccessibilityLiveRegion()!=View.ACCESSIBILITY_LIVE_REGION_POLITE)
            throw new AssertionError("Detection fault is not announced");
        if(width==320 && root.getResources().getConfiguration().fontScale==2f)screenshot(root,output);
        PreviewChrome.compact(root,true,false);PreviewChrome.detectionStopped(root,true,true);
        measure(root,96,128);textFits(warning);warningInsideCamera(root,warning);
        if(!warning.getText().toString().contains("未检测") || !warning.getText().toString().contains("仅预览"))
            throw new AssertionError("Compact detection fault lost its meaning");
        PreviewChrome.compact(root,false,depth);PreviewChrome.detectionStopped(root,false,false);
        root.setPadding(0,24,0,24);info.setText(previousInfo);title.setText(previousTitle);
        root.findViewById(R.id.recent_alert).setVisibility(previousAlert);measure(root,width,height);
        controls.scrollTo(0,previousScroll);
        if(warning.getVisibility()!=View.GONE)throw new AssertionError("Fault warning survived explicit clearing");
    }
    private static void warningInsideCamera(View root,View warning) {
        View camera=root.findViewById(R.id.preview_frame);
        Rect label=new Rect(0,0,warning.getWidth(),warning.getHeight()),frame=new Rect(0,0,camera.getWidth(),camera.getHeight());
        ((ViewGroup)root).offsetDescendantRectToMyCoords(warning,label);
        ((ViewGroup)root).offsetDescendantRectToMyCoords(camera,frame);
        if(warning.getVisibility()!=View.VISIBLE || label.isEmpty() || !frame.contains(label))
            throw new AssertionError("Detection status outside camera: "+label+" frame="+frame);
    }
    private static void initialQualifierVisible(View root,TextView info,String qualifier) {
        if(info.getLayout()==null || !info.getText().subSequence(0,info.getLayout().getLineEnd(0)).toString().contains(qualifier))
            throw new AssertionError("Distance qualifier lost from first line: "+info.getText());
        Rect first=new Rect(0,0,info.getWidth(),info.getCompoundPaddingTop()+info.getLayout().getLineBottom(0));
        View panel=root.findViewById(R.id.preview_controls);
        Rect viewport=new Rect(0,0,panel.getWidth(),panel.getHeight());
        ((ViewGroup)root).offsetDescendantRectToMyCoords(info,first);
        ((ViewGroup)root).offsetDescendantRectToMyCoords(panel,viewport);
        if(!viewport.contains(first))throw new AssertionError("First distance qualifier outside initial viewport: "+first+" viewport="+viewport);
    }
    private static void initialVisible(View root,View view) {
        View scroll=root.findViewById(R.id.preview_controls);
        Rect bounds=new Rect(0,0,view.getWidth(),view.getHeight()),viewport=new Rect(0,0,scroll.getWidth(),scroll.getHeight());
        ((ViewGroup)root).offsetDescendantRectToMyCoords(view,bounds);
        ((ViewGroup)root).offsetDescendantRectToMyCoords(scroll,viewport);
        if(!viewport.contains(bounds))throw new AssertionError("Initial control outside panel: "+bounds+" viewport="+viewport);
        if(view instanceof CheckBox && (bounds.height()<48 || bounds.width()<48))throw new AssertionError("Small toggle target");
    }
    private static void textFits(View view) {
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView && view.getId()!=R.id.recent_alert && view.getId()!=R.id.pip_depth_status) {
            TextView text=(TextView)view;
            if(text.getLayout()==null || text.getLayout().getHeight()>text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom())
                throw new AssertionError("Text vertically clipped: "+text.getText());
            for(int line=0;line<text.getLineCount();line++)if(text.getLayout().getEllipsisCount(line)>0)
                throw new AssertionError("Text ellipsized: "+text.getText());
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)textFits(((ViewGroup)view).getChildAt(i));
    }

    private static void checkFeedback(View root) {
        TextView details=root.findViewById(R.id.preview_info),banner=root.findViewById(R.id.recent_alert);
        if(details.getAccessibilityLiveRegion()!=View.ACCESSIBILITY_LIVE_REGION_NONE
            || banner.getAccessibilityLiveRegion()!=View.ACCESSIBILITY_LIVE_REGION_POLITE)
            throw new AssertionError("Detection feedback has duplicate or missing live regions");
        RiskAnnouncer announcer=new RiskAnnouncer(root);
        announcer.update("候选",Detection.Risk.CAPTURED,false,100);
        if(banner.getVisibility()!=View.GONE)throw new AssertionError("Gray candidate created a warning");
        announcer.update("画面关注 · 人 · 未测距",Detection.Risk.CAUTION,true,200);
        if(banner.getVisibility()!=View.VISIBLE)throw new AssertionError("Missing confirmed warning");
        announcer.update("近处注意 · 约 0.7 米",Detection.Risk.NEAR,false,201);
        if(!banner.getContentDescription().toString().contains("0.7")
            || !banner.getText().toString().contains("近处注意"))throw new AssertionError("Risk upgrade was suppressed");
        String recent=banner.getContentDescription().toString();
        for(int i=0;i<100;i++)announcer.update("新距离 "+i,Detection.Risk.NEAR,false,300+i);
        if(!recent.contentEquals(banner.getContentDescription()))throw new AssertionError("Every frame updated the live warning");
        announcer.update("黄",Detection.Risk.CAUTION,false,400);
        if(banner.getVisibility()!=View.GONE)throw new AssertionError("Old red warning survived downgrade");
        announcer.clear();announcer.update("无目标",Detection.Risk.CAPTURED,false,500);
        if(banner.getVisibility()!=View.GONE)throw new AssertionError("Warning survived stop/clear");
        announcer.update("画面关注 · 人 · 中间 · 未测距",Detection.Risk.CAUTION,true,600);
    }

    private static void checkRecovery(View root,int width,int height,File screenshot) throws Exception {
        root.findViewById(R.id.recent_alert).setVisibility(View.GONE);
        ((TextView)root.findViewById(R.id.preview_title)).setText(R.string.window_idle);
        TextView cover=root.findViewById(R.id.preview_cover),action=root.findViewById(R.id.restart_preview);
        for(RecoveryGuide.Reason reason:RecoveryGuide.Reason.values())for(boolean granted:new boolean[]{false,true}) {
            RecoveryGuide.Prompt prompt=RecoveryGuide.resolve(reason,granted);
            PreviewRecovery.show(root,prompt);measure(root,width,height);
            fullyVisible(root,action);fullyVisible(root,root.findViewById(R.id.preview_stop));
            if(!prompt.button.contentEquals(action.getText()) || action.isEnabled()!=(prompt.action!=RecoveryGuide.Action.WAIT))
                throw new AssertionError("Wrong recovery action: "+reason);
            if(root.findViewById(R.id.preview_recognition).isEnabled() || root.findViewById(R.id.preview_sound).isEnabled()
                || root.findViewById(R.id.enable_depth).isEnabled())
                throw new AssertionError("Detection controls stayed active on a stopped preview");
            if(cover.getVisibility()!=View.VISIBLE || !prompt.title.contentEquals(cover.getText())
                || cover.getLayout().getHeight()>cover.getHeight()-cover.getPaddingTop()-cover.getPaddingBottom())
                throw new AssertionError("Recovery cover title clipped: "+reason+" at "+width+"x"+height);
            if(reason==RecoveryGuide.Reason.DARK && granted) {
                ((ScrollView)root.findViewById(R.id.preview_controls)).scrollTo(0,0);
                screenshot(root,screenshot);
            }
        }
    }

    private static void checkDisabledButton(View button) {
        Drawable drawable=button.getBackground().getConstantState().newDrawable().mutate();
        Bitmap bitmap=Bitmap.createBitmap(100,60,Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas=new Canvas(bitmap);drawable.setBounds(0,0,100,60);
            drawable.setState(new int[]{android.R.attr.state_enabled});drawable.draw(canvas);
            int enabled=bitmap.getPixel(50,30);bitmap.eraseColor(Color.TRANSPARENT);
            drawable.setState(new int[]{});drawable.draw(canvas);
            if(enabled==bitmap.getPixel(50,30))throw new AssertionError("Disabled primary button looks enabled");
        } finally {bitmap.recycle();}
    }

    private static void measure(View root,int w,int h) {
        root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
        root.layout(0,0,w,h);
    }
    private static void fullyVisible(View root,View control) {
        Rect rect=new Rect(0,0,control.getWidth(),control.getHeight());
        ((ViewGroup)root).offsetDescendantRectToMyCoords(control,rect);
        if(control.getVisibility()!=View.VISIBLE || rect.left<0 || rect.top<0 || rect.right>root.getWidth()
            || rect.bottom>root.getHeight() || rect.width()<48 || rect.height()<48)
            throw new AssertionError("Fixed action clipped: "+rect);
    }
    private static void screenshot(View root,File output) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        try(FileOutputStream stream=new FileOutputStream(output)) {
            root.draw(new Canvas(bitmap));bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);
        } finally {bitmap.recycle();}
    }
}
