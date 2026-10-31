package com.cyanrbt.walkbuddy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Non-exported PiP controls. Sound changes never start a camera or detection. */
public final class StopPreviewReceiver extends BroadcastReceiver {
    static final String STOP_PREVIEW="com.cyanrbt.walkbuddy.STOP_PREVIEW";
    static final String STOP_DETECTION="com.cyanrbt.walkbuddy.STOP_DETECTION";
    static final String SET_SOUND="com.cyanrbt.walkbuddy.SET_SOUND";
    static final String OWNER="owner",GENERATION="generation";
    static final String SOUND_REVISION="sound_revision",PIP_VISIT="pip_visit",SOUND_ENABLED="sound_enabled";
    @Override public void onReceive(Context context, Intent intent) {
        if(intent==null)return;
        if(STOP_PREVIEW.equals(intent.getAction()))PreviewActivity.stopActivePreview();
        else if(STOP_DETECTION.equals(intent.getAction()))PreviewActivity.stopPipDetection(
            intent.getStringExtra(OWNER),intent.getLongExtra(GENERATION,-1));
        else if(SET_SOUND.equals(intent.getAction())) {
            if(!intent.hasExtra(SOUND_ENABLED)) {
                android.util.Log.i("WalkBuddyPipSound","command elapsedMs="+android.os.SystemClock.elapsedRealtime()
                    +" result=rejected_missing_enabled");
                return;
            }
            PreviewActivity.setPipSound(intent.getStringExtra(OWNER),intent.getLongExtra(GENERATION,-1),
                intent.getLongExtra(SOUND_REVISION,-1),intent.getLongExtra(PIP_VISIT,-1),intent.getBooleanExtra(SOUND_ENABLED,false));
        }
    }
}
