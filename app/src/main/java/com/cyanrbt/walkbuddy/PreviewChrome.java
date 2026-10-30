package com.cyanrbt.walkbuddy;

import android.view.View;

/** Shared by the Activity and real Android layout checks, without opening the camera. */
final class PreviewChrome {
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
