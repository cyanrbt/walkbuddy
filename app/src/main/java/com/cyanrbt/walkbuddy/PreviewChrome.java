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
        detectionStopped(root,PreviewStatus.showNotice(live,recognition,stoppedNotice,compact),compact);
    }
    static void detectionStopped(View root,boolean stopped,boolean compact) {
        android.widget.TextView warning=root.findViewById(R.id.detection_status);
        warning.setVisibility(stopped?View.VISIBLE:View.GONE);
        int text=compact?R.string.detection_stopped_compact:R.string.detection_stopped_preview_only;
        CharSequence label=root.getResources().getText(text);
        if(!android.text.TextUtils.equals(warning.getText(),label))warning.setText(label);
        boolean depth=root.findViewById(R.id.depth_steps).getVisibility()==View.VISIBLE;
        root.findViewById(R.id.pip_depth_status).setVisibility(compact && depth && !stopped?View.VISIBLE:View.GONE);
        if(compact && stopped)root.findViewById(R.id.posture_hint).setVisibility(View.GONE);
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
