package com.cyanrbt.walkbuddy;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Native home resource geometry plus the real idle Activity/recreation path. No camera is opened. */
final class HomeProbe {
    static void run(Instrumentation runner) {
        Bundle result=new Bundle();StringBuilder report=new StringBuilder();Activity activity=null;
        try {
            if(!(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator")))
                throw new IllegalStateException("Home probe is restricted to emulators");
            read(runner,()->{layouts(runner.getTargetContext(),report);return true;});
            activity=runner.startActivitySync(new Intent(runner.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity first=activity;runner.waitForIdleSync();
            awaitIdle(runner,first);
            read(runner,()->{
                if(PreviewActivity.hasPreview() || first.findViewById(R.id.stop).getVisibility()!=View.GONE)
                    throw new AssertionError("Idle home offered a camera stop");
                first.findViewById(R.id.more_toggle).performClick();
                if(first.findViewById(R.id.more_panel).getVisibility()!=View.VISIBLE)throw new AssertionError("More did not expand");
                return true;
            });
            Instrumentation.ActivityMonitor monitor=runner.addMonitor(MainActivity.class.getName(),null,false);
            try {
                runner.runOnMainSync(first::recreate);activity=runner.waitForMonitorWithTimeout(monitor,7000);
                if(activity==null || activity==first)throw new AssertionError("Home did not recreate");
            } finally {runner.removeMonitor(monitor);}
            Activity restored=activity;runner.waitForIdleSync();awaitIdle(runner,restored);
            read(runner,()->{
                if(restored.findViewById(R.id.more_panel).getVisibility()!=View.VISIBLE
                    || !((TextView)restored.findViewById(R.id.more_toggle)).getText().toString().contains("收起"))
                    throw new AssertionError("Expanded guide was not restored");
                fixed(restored.findViewById(R.id.home_root),restored.findViewById(R.id.start));
                if(PreviewActivity.hasPreview())throw new AssertionError("Home recreation opened a preview");return true;
            });
            report.append("HOME_PROBE_PASS configurations=24 action-states=240 expanded=240; brand reflow, first step, current status, fixed controls, full text, contrast, idle refresh stopped, real Activity recreation\n");
            result.putString("stream",report.toString());
        } catch(Throwable error){result.putString("stream",report+"HOME_PROBE_FAIL: "+error);}
        finally {if(activity!=null){Activity host=activity;runner.runOnMainSync(host::finish);}}
        runner.finish(result.getString("stream").contains("HOME_PROBE_PASS")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }

    private static void layouts(Context target,StringBuilder report)throws Exception {
        File output=new File(target.getCacheDir(),"ui-probe");if(!output.isDirectory() && !output.mkdirs())throw new IOException("Evidence directory unavailable");
        for(int[] size:new int[][]{{320,480},{360,640},{640,360},{800,600}})for(float font:new float[]{1,1.5f,2})for(boolean night:new boolean[]{false,true}) {
            Configuration c=new Configuration(target.getResources().getConfiguration());c.fontScale=font;c.densityDpi=160;c.screenWidthDp=size[0];c.screenHeightDp=size[1];
            c.orientation=size[0]>size[1]?Configuration.ORIENTATION_LANDSCAPE:Configuration.ORIENTATION_PORTRAIT;
            c.uiMode=(c.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(night?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
            Context context=new ContextThemeWrapper(target.createConfigurationContext(c),R.style.Theme_WalkBuddy_Home);
            String name="home-"+size[0]+"x"+size[1]+"-font"+Math.round(font*100)+(night?"-night":"-day");
            // Permission, settings, ready, opening, live, stopped actor, PiP settings,
            // completed idle, unsupported after preview, and revoked camera after completion.
            for(int mode=0;mode<10;mode++) {
                View root=LayoutInflater.from(context).inflate(R.layout.activity_main,null);root.setPadding(16,24,16,24);
                ScrollView scroll=root.findViewById(R.id.home_scroll);
                boolean camera=mode>=2 && mode!=9,present=mode>=3 && mode<=5;
                boolean seen=mode>=4,pipSeen=seen && mode!=8,supported=mode!=8,available=mode!=6 && mode!=8;
                SessionState.Phase phase=mode==3?SessionState.Phase.STARTING:mode==4?SessionState.Phase.LIVE:mode==5?SessionState.Phase.UNAVAILABLE:SessionState.Phase.STOPPED;
                HomeJourney journey=HomeJourney.resolve(camera,seen,pipSeen,supported,available);
                int hint=!camera?(mode==1 || mode==9?R.string.journey_hint_camera_settings:R.string.journey_hint_start):mode==6?R.string.journey_hint_pip_disabled:mode==8?R.string.journey_hint_in_app:seen?R.string.journey_hint_done:R.string.journey_hint_camera;
                HomeChrome.journey(root,journey,hint,camera && supported && !available);
                HomeChrome.actions(root,camera,mode==1 || mode==9,present,phase);
                ((TextView)root.findViewById(R.id.status)).setText(mode==4?"布局检查\n识别开着":mode==5?"布局检查 · 观察已停止，请返回预览按提示主动恢复":"布局检查 · 已停止");
                TextView brand=root.findViewById(R.id.home_brand_name);float chosenTextSize=brand.getTextSize();measure(root,size);
                if(mode==0 || mode==4)screenshot(root,new File(output,name+(mode==4?"-live":"-permission")+".png"));
                if(brand.getLineCount()!=1 || brand.getTextSize()!=chosenTextSize)throw new AssertionError("Wordmark wrapped or changed user text size: "+name);
                boolean compact=mode==4 || mode==5 || mode==7 || mode==8;
                if(root.findViewById(R.id.home_steps).getVisibility()!=(compact?View.GONE:View.VISIBLE))throw new AssertionError("Wrong onboarding presentation "+mode);
                if(root.findViewById(R.id.home_status_container).getVisibility()!=(compact || present?View.VISIBLE:View.GONE))throw new AssertionError("Wrong status presentation "+mode);
                if(!compact && bounds(root,root.findViewById(R.id.journey_hint)).top>=bounds(root,root.findViewById(R.id.home_steps)).top)throw new AssertionError("Current instruction follows history");
                View primary=root.findViewById(R.id.start),stop=root.findViewById(R.id.stop);
                fixed(root,primary);textFits((TextView)primary);
                if(stop.getVisibility()!=(mode==3 || mode==4?View.VISIBLE:View.GONE) || stop.isEnabled()!=(mode==3 || mode==4))throw new AssertionError("Wrong stop state "+mode);
                if(mode==3 || mode==4){fixed(root,stop);textFits((TextView)stop);}
                if(scroll.getHeight()<(size[1]-48)/2)throw new AssertionError("Home content collapsed behind actions");
                for(View parent=primary;parent!=root;parent=(View)parent.getParent())if(parent==scroll)throw new AssertionError("Primary action is still inside scrolling content");
                if(primary.getAccessibilityTraversalBefore()!=R.id.home_journey_label)throw new AssertionError("Missing action traversal anchor");
                // A live returning user should see the complete short status without scrolling.
                if(mode==4)fullyVisible(root,scroll,root.findViewById(R.id.status));
                if(mode==0 && size[0]==320 && size[1]==480)fullyVisible(root,scroll,root.findViewById(R.id.journey_hint));
                if(mode==0) {
                    int art=root.findViewById(R.id.home_brand_art).getVisibility();
                    measure(root,new int[]{800,600});
                    if(root.findViewById(R.id.home_brand_art).getVisibility()!=View.VISIBLE)throw new AssertionError("Wide layout failed to restore decoration");
                    measure(root,size);
                    if(root.findViewById(R.id.home_brand_art).getVisibility()!=art || brand.getTextSize()!=chosenTextSize)throw new AssertionError("Brand did not reflow on resize");
                }
                Rect before=bounds(root,primary);
                root.findViewById(R.id.more_panel).setVisibility(View.VISIBLE);measure(root,size);
                for(int id:new int[]{R.id.more_toggle,R.id.row_camera,R.id.row_pip}) {
                    View control=root.findViewById(id);scroll.requestChildFocus(control,control);Rect visible=new Rect();
                    if(!control.getLocalVisibleRect(visible) || visible.height()<Math.min(48,control.getHeight()))throw new AssertionError("Unreachable home control "+id);
                }
                scroll.fullScroll(View.FOCUS_DOWN);measure(root,size);fixed(root,primary);
                if(!before.equals(bounds(root,primary)))throw new AssertionError("Footer moved with expanded/scrolled content");
                allTextFits(root);
            }
            contrast(context,R.color.home_accent_text,R.color.paper_card);contrast(context,R.color.home_accent_text,R.color.paper);
            contrast(context,R.color.muted,R.color.paper_card);contrast(context,R.color.muted,R.color.paper);
            contrast(context,R.color.on_ink,R.color.mint);
            report.append("PASS ").append(name).append(" states=10; next step and compact status; full-size wordmark; primary fixed\n");
        }
    }
    private static void fullyVisible(View root,View viewport,View control) {
        Rect visible=bounds(root,viewport),text=bounds(root,control);
        if(!visible.contains(text))throw new AssertionError("Priority home content needs scrolling: "+text+" viewport="+visible);
    }
    private static void awaitIdle(Instrumentation runner,Activity host)throws Exception {
        long end=System.nanoTime()+5_000_000_000L;
        while(System.nanoTime()<end) {
            if(read(runner,()->!((Handler)field(host,"handler")).hasCallbacks((Runnable)field(host,"refresh"))))return;
            Thread.sleep(25);
        }
        throw new AssertionError("Idle home kept polling");
    }
    private static Object field(Object host,String name)throws Exception{Field f=host.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(host);}
    private interface Value<T>{T get()throws Exception;}
    private static <T>T read(Instrumentation runner,Value<T> fn) {
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runner.runOnMainSync(()->{try{result.set(fn.get());}catch(Throwable t){error.set(t);}});
        if(error.get()!=null)throw new AssertionError(error.get());return result.get();
    }
    private static void measure(View root,int[] s){root.measure(View.MeasureSpec.makeMeasureSpec(s[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(s[1],View.MeasureSpec.EXACTLY));root.layout(0,0,s[0],s[1]);}
    private static Rect bounds(View root,View control){Rect r=new Rect(0,0,control.getWidth(),control.getHeight());((ViewGroup)root).offsetDescendantRectToMyCoords(control,r);return r;}
    private static void fixed(View root,View control) {
        Rect r=bounds(root,control);
        if(control.getVisibility()!=View.VISIBLE || r.width()<48 || r.height()<48 || r.left<root.getPaddingLeft() || r.top<root.getPaddingTop()
            || r.right>root.getWidth()-root.getPaddingRight() || r.bottom>root.getHeight()-root.getPaddingBottom())throw new AssertionError("Home action clipped: "+r);
    }
    private static void allTextFits(View view) {
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView)textFits((TextView)view);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)allTextFits(((ViewGroup)view).getChildAt(i));
    }
    private static void textFits(TextView text) {
        if(text.getLayout()==null || text.getLayout().getHeight()>text.getHeight()-text.getPaddingTop()-text.getPaddingBottom())throw new AssertionError("Text clipped: "+text.getText());
        for(int i=0;i<text.getLineCount();i++)if(text.getLayout().getEllipsisCount(i)>0)throw new AssertionError("Text truncated: "+text.getText());
    }
    private static double luminance(int color){double[] channels={Color.red(color)/255d,Color.green(color)/255d,Color.blue(color)/255d};for(int i=0;i<3;i++)channels[i]=channels[i]<=0.04045?channels[i]/12.92:Math.pow((channels[i]+0.055)/1.055,2.4);return channels[0]*.2126+channels[1]*.7152+channels[2]*.0722;}
    private static void contrast(Context c,int a,int b){double x=luminance(c.getColor(a)),y=luminance(c.getColor(b));double ratio=(Math.max(x,y)+.05)/(Math.min(x,y)+.05);if(ratio<4.5)throw new AssertionError("Home text contrast below 4.5: "+ratio);}
    private static void screenshot(View root,File file)throws Exception{Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);try(FileOutputStream out=new FileOutputStream(file)){root.draw(new Canvas(b));b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
}
