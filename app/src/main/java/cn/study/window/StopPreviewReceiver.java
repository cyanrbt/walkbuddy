package cn.study.window;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Non-exported PiP action endpoint on every supported Android version. Never starts a camera. */
public final class StopPreviewReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PreviewActivity.stopActivePreview();
    }
}
