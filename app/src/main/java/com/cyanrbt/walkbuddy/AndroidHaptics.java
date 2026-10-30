package com.cyanrbt.walkbuddy;

import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Optional device output; a missing or rejected vibration must not stop the camera. */
final class AndroidHaptics implements RiskHaptics.Output {
    private final Vibrator vibrator;
    AndroidHaptics(Vibrator vibrator){this.vibrator=vibrator;}

    public void play(RiskHaptics.Cue cue) {
        if(vibrator==null || cue==RiskHaptics.Cue.NONE)return;
        try {if(vibrator.hasVibrator())vibrator.vibrate(effect(cue));}
        catch(RuntimeException ignored) { /* Device feedback is optional. */ }
    }
    public void cancel() {
        try {if(vibrator!=null)vibrator.cancel();}
        catch(RuntimeException ignored) { /* Keep stop/recovery available. */ }
    }
    static VibrationEffect effect(RiskHaptics.Cue cue) {
        if(cue==RiskHaptics.Cue.NONE)return null;
        if(Build.VERSION.SDK_INT>=29)return VibrationEffect.createPredefined(
            cue==RiskHaptics.Cue.DOUBLE?VibrationEffect.EFFECT_DOUBLE_CLICK:VibrationEffect.EFFECT_CLICK);
        return fallbackEffect(cue);
    }
    static VibrationEffect fallbackEffect(RiskHaptics.Cue cue) {
        return cue==RiskHaptics.Cue.NONE?null:VibrationEffect.createWaveform(cue.fallbackTimings(),-1);
    }
}
