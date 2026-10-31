package com.cyanrbt.walkbuddy;

import android.view.View;

/** Shared by the Activity and real Android layout checks, without opening the camera. */
final class PreviewChrome {
    static void title(android.widget.TextView title,PreviewStatus.Heading heading) {
        int text=switch(heading) {
            case IDLE -> R.string.window_idle;
            case STARTING -> R.string.window_starting;
            case PREVIEW_ONLY -> R.string.window_preview_only;
            case LIVE -> R.string.window_live;
        };
        CharSequence label=title.getResources().getText(text);
        if(!android.text.TextUtils.equals(title.getText(),label))title.setText(label);
    }
    static void detectionStatus(View root,boolean live,boolean recognition,boolean stoppedNotice,boolean compact) {
        detectionStatus(root,live,recognition,stoppedNotice,compact,false);
    }
    static void detectionStatus(View root,boolean live,boolean recognition,boolean stoppedNotice,boolean compact,boolean enlarged) {
        detectionStopped(root,PreviewStatus.showNotice(live,recognition,stoppedNotice,compact,enlarged),compact,enlarged,
            enlarged && !stoppedNotice?R.string.detection_off_enlarged:R.string.detection_stopped_preview_only);
    }
    static void detectionStopped(View root,boolean stopped,boolean compact) {
        detectionStopped(root,stopped,compact,false,R.string.detection_stopped_preview_only);
    }
    private static void detectionStopped(View root,boolean stopped,boolean compact,boolean enlarged,int fullText) {
        android.widget.TextView warning=root.findViewById(R.id.detection_status);
        warning.setVisibility(stopped?View.VISIBLE:View.GONE);
        int text=compact?R.string.detection_stopped_compact:fullText;
        CharSequence label=root.getResources().getText(text);
        if(!android.text.TextUtils.equals(warning.getText(),label))warning.setText(label);
        boolean depth=root.findViewById(R.id.depth_steps).getVisibility()==View.VISIBLE;
        root.findViewById(R.id.pip_depth_status).setVisibility((compact || enlarged) && depth && !stopped?View.VISIBLE:View.GONE);
        if(compact && stopped)root.findViewById(R.id.posture_hint).setVisibility(View.GONE);
    }
    static void viewfinder(View root,boolean compact,boolean live,boolean requested) {
        boolean enlarged=live && requested && !compact;
        root.findViewById(R.id.preview_name).setVisibility(enlarged?View.GONE:View.VISIBLE);
        View header=root.findViewById(R.id.preview_header);
        int inset=Math.round((enlarged?4:8)*root.getResources().getDisplayMetrics().density);
        if(header.getPaddingTop()!=inset || header.getPaddingBottom()!=inset)
            header.setPadding(header.getPaddingLeft(),inset,header.getPaddingRight(),inset);
        root.findViewById(R.id.preview_controls).setVisibility(compact || enlarged?View.GONE:View.VISIBLE);
        android.widget.ImageButton toggle=root.findViewById(R.id.viewfinder_toggle);
        toggle.setEnabled(live && !compact);toggle.setAlpha(live && !compact?1f:.45f);
        if(toggle.isSelected()!=enlarged) {
            toggle.setSelected(enlarged);
            toggle.setImageResource(enlarged?R.drawable.ic_restore_controls:R.drawable.ic_expand_preview);
        }
        CharSequence description=root.getResources().getText(enlarged?R.string.viewfinder_controls:R.string.viewfinder_expand);
        if(!android.text.TextUtils.equals(toggle.getContentDescription(),description))toggle.setContentDescription(description);
        if(!android.text.TextUtils.equals(toggle.getTooltipText(),description))toggle.setTooltipText(description);
    }
    static void compact(View root,boolean compact,boolean depth) {
        depth(root,depth);
        int visibility=compact?View.GONE:View.VISIBLE;
        root.findViewById(R.id.preview_header).setVisibility(visibility);
        root.findViewById(R.id.preview_actions).setVisibility(visibility);
        root.findViewById(R.id.preview_controls).setVisibility(visibility);
        root.findViewById(R.id.pip_depth_status).setVisibility(compact && depth?View.VISIBLE:View.GONE);
        if(compact)root.setPadding(0,0,0,0);
    }

    static void depth(View root,boolean enabled) {
        int visibility=enabled?View.VISIBLE:View.GONE;
        View steps=root.findViewById(R.id.depth_steps),status=root.findViewById(R.id.depth_status);
        if(steps.getVisibility()!=visibility)steps.setVisibility(visibility);
        if(status.getVisibility()!=visibility)status.setVisibility(visibility);
    }

    static void showRestart(View root,boolean restart) {
        root.findViewById(R.id.restart_preview).setVisibility(restart?View.VISIBLE:View.GONE);
        root.findViewById(R.id.enter_pip).setVisibility(restart?View.GONE:View.VISIBLE);
    }
}
