package com.cyanrbt.walkbuddy;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import java.io.File;

/** Constructed native layouts for large fonts, viewport space and essential status/actions. */
final class ViewfinderLayoutProbe {
    static void check(View root,int width,int height,File output,String name) throws Exception {
        CheckBox detection=root.findViewById(R.id.preview_recognition),sound=root.findViewById(R.id.preview_sound),vibration=root.findViewById(R.id.preview_vibration);
        boolean rec=detection.isChecked(),audio=sound.isChecked(),haptics=vibration.isChecked();
        boolean depth=root.findViewById(R.id.depth_steps).getVisibility()==View.VISIBLE;
        TextView depthCue=root.findViewById(R.id.pip_depth_status);CharSequence oldDepth=depthCue.getText();
        PreviewChrome.compact(root,false,false);PreviewChrome.viewfinder(root,false,true,false);measure(root,width,height);
        View camera=root.findViewById(R.id.preview_frame);long originalArea=area(camera);
        try {
            detection.setChecked(false);sound.setChecked(false);
            PreviewChrome.viewfinder(root,false,true,true);PreviewChrome.detectionStatus(root,true,false,false,false,true);
            measure(root,width,height);
            if(area(camera)<=originalArea || root.findViewById(R.id.preview_controls).getVisibility()!=View.GONE)
                throw new AssertionError("Enlargement did not give space back to the camera: "+name);
            for(int id:new int[]{R.id.viewfinder_toggle,R.id.preview_stop,R.id.enter_pip})visible(root,root.findViewById(id));
            View toggle=root.findViewById(R.id.viewfinder_toggle);
            if(!toggle.isSelected() || !toggle.isEnabled() || !root.getContext().getText(R.string.viewfinder_controls).equals(toggle.getContentDescription()))
                throw new AssertionError("Missing enlarged restore action");
            TextView warning=root.findViewById(R.id.detection_status);
            if(warning.getVisibility()!=View.VISIBLE || !root.getContext().getText(R.string.detection_off_enlarged).equals(warning.getText()))
                throw new AssertionError("Enlarged preview does not disclose detection is off");
            textFits(warning);RecoveryPresentationProbe.image(root,new File(output,name+"-viewfinder-preview.png"));

            // Choices remain checked even while their controls are hidden.
            detection.setChecked(true);sound.setChecked(true);vibration.setChecked(false);
            PreviewChrome.depth(root,true);depthCue.setText("地面已确认 · 等待深度");
            PreviewChrome.viewfinder(root,false,true,true);PreviewChrome.detectionStatus(root,true,true,false,false,true);measure(root,width,height);
            if(!detection.isChecked() || !sound.isChecked() || vibration.isChecked() || warning.getVisibility()!=View.GONE
                || depthCue.getVisibility()!=View.VISIBLE)throw new AssertionError("Hidden settings changed choices or lost depth status");
            textFits(depthCue);RecoveryPresentationProbe.image(root,new File(output,name+"-viewfinder-depth.png"));

            PreviewChrome.compact(root,true,true);PreviewChrome.viewfinder(root,true,true,true);
            measure(root,180,240);
            if(camera.getWidth()!=180 || camera.getHeight()!=240 || root.findViewById(R.id.preview_controls).getVisibility()!=View.GONE)
                throw new AssertionError("Enlarged choice leaked controls into compact view");

            PreviewChrome.compact(root,false,false);root.setPadding(0,24,0,24);
            PreviewChrome.showRestart(root,true);
            RecoveryGuide.Prompt prompt=RecoveryGuide.resolve(RecoveryGuide.Reason.GENERAL,true);
            PreviewRecovery.show(root,prompt,false);measure(root,width,height);
            if(root.findViewById(R.id.preview_controls).getVisibility()!=View.VISIBLE || toggle.isEnabled() || toggle.isSelected())
                throw new AssertionError("Recovery stayed hidden behind enlarged view");
            RecoveryPresentationProbe.full(root,prompt);
        } finally {
            PreviewRecovery.clear(root);PreviewChrome.compact(root,false,depth);root.setPadding(0,24,0,24);
            PreviewChrome.showRestart(root,false);root.findViewById(R.id.preview_cover).setVisibility(View.GONE);
            detection.setChecked(rec);sound.setChecked(audio);vibration.setChecked(haptics);
            detection.setEnabled(true);sound.setEnabled(rec);vibration.setEnabled(true);root.findViewById(R.id.enable_depth).setEnabled(true);
            PreviewChrome.viewfinder(root,false,true,false);PreviewChrome.detectionStatus(root,true,rec,false,false);depthCue.setText(oldDepth);
            measure(root,width,height);
        }
        if(area(camera)!=originalArea)throw new AssertionError("Restored viewport differs from original: "+name);
    }
    private static long area(View view){return (long)view.getWidth()*view.getHeight();}
    private static void visible(View root,View view) {
        Rect bounds=new Rect(0,0,view.getWidth(),view.getHeight());((ViewGroup)root).offsetDescendantRectToMyCoords(view,bounds);
        for(View node=view;;) {
            if(node.getVisibility()!=View.VISIBLE)throw new AssertionError("Enlarged action has a hidden ancestor");
            if(node==root)break;
            if(!(node.getParent() instanceof View))throw new AssertionError("Action is outside the measured root");
            node=(View)node.getParent();
        }
        if(bounds.left<0 || bounds.top<0 || bounds.right>root.getWidth() || bounds.bottom>root.getHeight()
            || bounds.width()<48 || bounds.height()<48)throw new AssertionError("Enlarged action is clipped: "+bounds);
    }
    private static void textFits(TextView text) {
        if(text.getLayout()==null || text.getLayout().getHeight()>text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom())
            throw new AssertionError("Enlarged cue vertically clipped");
        for(int i=0;i<text.getLineCount();i++)if(text.getLayout().getEllipsisCount(i)>0)
            throw new AssertionError("Enlarged cue ellipsized");
    }
    private static void measure(View root,int width,int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
    }
}
