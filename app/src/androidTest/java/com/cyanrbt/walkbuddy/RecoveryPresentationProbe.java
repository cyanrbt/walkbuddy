package com.cyanrbt.walkbuddy;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.text.Layout;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Native text geometry and state transitions; no camera or synthetic font measurement. */
final class RecoveryPresentationProbe {
    static void full(View root,RecoveryGuide.Prompt prompt) {
        common(root);
        TextView cover=root.findViewById(R.id.preview_cover),hint=root.findViewById(R.id.recovery_hint);
        View frame=root.findViewById(R.id.preview_frame),pane=root.findViewById(R.id.preview_content);
        if(!prompt.title.contentEquals(cover.getText()) || cover.getContentDescription()!=null)
            throw new AssertionError("Full recovery did not restore its reason");
        if(!prompt.body.contentEquals(hint.getText()) || hint.getVisibility()!=View.VISIBLE)
            throw new AssertionError("Recovery instructions missing");
        if(frame.getWidth()!=pane.getWidth() || frame.getHeight()>=pane.getHeight()
            || root.findViewById(R.id.preview_controls).getHeight()!=pane.getHeight()-frame.getHeight())
            throw new AssertionError("Stopped recovery wasted its instruction viewport");
        textFits(cover);style(cover,16,16);
        TextView action=root.findViewById(R.id.restart_preview);
        if(action.getVisibility()!=View.VISIBLE || !prompt.button.contentEquals(action.getText())
            || action.isEnabled()!=(prompt.action!=RecoveryGuide.Action.WAIT))
            throw new AssertionError("Full recovery action changed");
    }
    static void compact(View root,RecoveryGuide.Prompt prompt) {
        common(root);
        TextView cover=root.findViewById(R.id.preview_cover);
        CharSequence expected=root.getContext().getText(prompt.action==RecoveryGuide.Action.WAIT
            ?R.string.recovery_compact_wait:R.string.recovery_compact_stopped);
        if(!expected.equals(cover.getText()) || cover.getLineCount()!=2)
            throw new AssertionError("Compact recovery must display both short lines: "+cover.getText());
        CharSequence description=cover.getContentDescription();
        if(description==null || !description.toString().contains(prompt.title)
            || !description.toString().contains(prompt.body) || !description.toString().contains("展开"))
            throw new AssertionError("Compact recovery lost accessible reason/instructions");
        for(int id:new int[]{R.id.preview_header,R.id.preview_actions,R.id.preview_controls})
            if(root.findViewById(id).getVisibility()!=View.GONE)throw new AssertionError("Full controls survived compact recovery");
        textFits(cover);style(cover,12,8);
        if(cover.getLineSpacingExtra()!=0)throw new AssertionError("Compact recovery retained large line spacing");
        View frame=root.findViewById(R.id.preview_frame);
        if(frame.getWidth()!=root.getWidth() || frame.getHeight()!=root.getHeight())
            throw new AssertionError("Compact recovery does not fill the window");
    }
    static boolean laidOut(View root) {
        TextView cover=root.findViewById(R.id.preview_cover);
        return root.getWidth()>0 && root.getHeight()>0 && !root.isLayoutRequested()
            && !cover.isLayoutRequested() && cover.getLayout()!=null;
    }
    static void preview(View root) {
        for(int id:new int[]{R.id.preview_live_settings,R.id.preview_optional_settings})
            if(root.findViewById(id).getVisibility()!=View.VISIBLE)throw new AssertionError("Restart did not restore settings");
        TextView cover=root.findViewById(R.id.preview_cover);style(cover,16,16);
        if(cover.getContentDescription()!=null)throw new AssertionError("Restart retained stopped accessibility description");
    }
    static void fixtures(View root,RecoveryGuide.Prompt prompt,int width,int height,File screenshot,boolean record) throws Exception {
        int left=root.getPaddingLeft(),top=root.getPaddingTop(),right=root.getPaddingRight(),bottom=root.getPaddingBottom();
        for(int[] size:new int[][]{{96,128},{116,154}}) {
            PreviewChrome.compact(root,true,true);PreviewRecovery.show(root,prompt,true);
            measure(root,size[0],size[1]);compact(root,prompt);
            if(record) {
                String suffix=screenshot.getName().substring(screenshot.getName().indexOf("-font"),screenshot.getName().indexOf("-recovery"));
                File image=new File(screenshot.getParentFile(),"recovery-compact-"+size[0]+"x"+size[1]+suffix+".png");
                image(root,image);
                TextView cover=root.findViewById(R.id.preview_cover);
                JSONObject sample=new JSONObject().put("width",size[0]).put("height",size[1])
                    .put("fontScale",root.getResources().getConfiguration().fontScale)
                    .put("text",cover.getText()).put("textSizePx",cover.getTextSize()).put("lineCount",cover.getLineCount())
                    .put("textHeight",cover.getLayout().getHeight()).put("availableTextHeight",cover.getHeight()-cover.getPaddingTop()-cover.getPaddingBottom())
                    .put("fits",true).put("image",image.getName());
                try(FileOutputStream out=new FileOutputStream(new File(image.getPath().replace(".png",".json")))) {
                    out.write(sample.toString(2).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        PreviewChrome.compact(root,false,false);root.setPadding(left,top,right,bottom);
        PreviewRecovery.show(root,prompt,false);measure(root,width,height);full(root,prompt);
    }
    static void initiallyReadable(View root) {
        ScrollView scroll=root.findViewById(R.id.preview_controls);
        TextView hint=root.findViewById(R.id.recovery_hint);
        Rect visible=new Rect();
        if(scroll.getScrollY()!=0 || !hint.getLocalVisibleRect(visible)
            || visible.height()<hint.getCompoundPaddingTop()+hint.getLayout().getLineBottom(0))
            throw new AssertionError("Recovery instructions did not start at their first readable line");
    }
    private static void common(View root) {
        TextView cover=root.findViewById(R.id.preview_cover);View frame=root.findViewById(R.id.preview_frame);
        if(cover.getVisibility()!=View.VISIBLE || cover.getWidth()!=frame.getWidth() || cover.getHeight()!=frame.getHeight()
            || !(cover.getBackground() instanceof ColorDrawable) || ((ColorDrawable)cover.getBackground()).getColor()!=0xff071410)
            throw new AssertionError("Stopped camera lacks a full opaque cover");
        for(int id:new int[]{R.id.preview_live_settings,R.id.preview_optional_settings,R.id.recent_alert,R.id.posture_hint,R.id.pip_depth_status})
            if(root.findViewById(id).getVisibility()!=View.GONE)throw new AssertionError("Stopped recovery exposed obsolete controls/overlays: "+id);
        for(int id:new int[]{R.id.preview_recognition,R.id.preview_sound,R.id.preview_vibration,R.id.enable_depth,R.id.viewfinder_toggle})
            if(root.findViewById(id).isEnabled())throw new AssertionError("Stopped recovery enabled a camera setting");
    }
    private static void textFits(TextView text) {
        Layout layout=text.getLayout();
        if(layout==null || layout.getHeight()>text.getHeight()-text.getPaddingTop()-text.getPaddingBottom())
            throw new AssertionError("Recovery text vertically clipped: "+text.getWidth()+"x"+text.getHeight()+" text="+text.getText());
        int width=text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight();
        for(int i=0;i<layout.getLineCount();i++)if(layout.getEllipsisCount(i)!=0 || layout.getLineWidth(i)>width+.5f)
            throw new AssertionError("Recovery text horizontally clipped");
    }
    private static void style(TextView cover,int sp,int dp) {
        TextView expected=new TextView(cover.getContext());expected.setTextSize(sp);
        int pad=Math.round(dp*cover.getResources().getDisplayMetrics().density);
        if(Math.abs(cover.getTextSize()-expected.getTextSize())>.01f || cover.getPaddingLeft()!=pad || cover.getPaddingTop()!=pad
            || cover.getPaddingRight()!=pad || cover.getPaddingBottom()!=pad)
            throw new AssertionError("Recovery font/padding did not restore: "+sp+"sp "+dp+"dp");
    }
    private static void measure(View root,int width,int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        root.layout(0,0,width,height);
    }
    static void image(View root,File output) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        try(FileOutputStream stream=new FileOutputStream(output)) {
            root.draw(new Canvas(bitmap));bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);
        } finally {bitmap.recycle();}
    }
}
