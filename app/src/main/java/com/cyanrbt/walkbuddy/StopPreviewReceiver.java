package com.cyanrbt.walkbuddy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Non-exported, stop-only PiP controls. Never starts a camera or detection. */
public final class StopPreviewReceiver extends BroadcastReceiver {
    static final String STOP_PREVIEW="com.cyanrbt.walkbuddy.STOP_PREVIEW";
    static final String STOP_DETECTION="com.cyanrbt.walkbuddy.STOP_DETECTION";
    static final String OWNER="owner",GENERATION="generation";
    @Override public void onReceive(Context context, Intent intent) {
        if(intent==null)return;
        if(STOP_PREVIEW.equals(intent.getAction()))PreviewActivity.stopActivePreview();
        else if(STOP_DETECTION.equals(intent.getAction()))PreviewActivity.stopPipDetection(
            intent.getStringExtra(OWNER),intent.getLongExtra(GENERATION,-1));
    }
}
