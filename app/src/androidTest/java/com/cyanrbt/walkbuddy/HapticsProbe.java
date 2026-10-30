package com.cyanrbt.walkbuddy;

import android.os.Parcel;
import android.os.VibrationEffect;

/** Checks Android effect construction without claiming a physical motor was exercised. */
final class HapticsProbe {
    static final class Output implements RiskHaptics.Output {
        RiskHaptics.Cue last=RiskHaptics.Cue.NONE;
        int plays,cancels;
        public void play(RiskHaptics.Cue cue){last=cue;plays++;}
        public void cancel(){cancels++;}
    }
    static void checkEffects() {
        if(AndroidHaptics.effect(RiskHaptics.Cue.NONE)!=null
            || AndroidHaptics.fallbackEffect(RiskHaptics.Cue.NONE)!=null)throw new AssertionError("Gray created a vibration effect");
        VibrationEffect[] actual={AndroidHaptics.effect(RiskHaptics.Cue.SINGLE),AndroidHaptics.effect(RiskHaptics.Cue.DOUBLE),
            AndroidHaptics.fallbackEffect(RiskHaptics.Cue.SINGLE),AndroidHaptics.fallbackEffect(RiskHaptics.Cue.DOUBLE)};
        VibrationEffect[] expected={VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK),
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK),
            VibrationEffect.createWaveform(new long[]{0,150},-1),VibrationEffect.createWaveform(new long[]{0,100,100,150},-1)};
        for(int i=0;i<actual.length;i++) {
            if(!actual[i].equals(expected[i]))throw new AssertionError("Wrong Android haptic effect "+i);
            Parcel parcel=Parcel.obtain();
            try {
                actual[i].writeToParcel(parcel,0);parcel.setDataPosition(0);
                if(!actual[i].equals(VibrationEffect.CREATOR.createFromParcel(parcel)))throw new AssertionError("Haptic parcel changed effect "+i);
            } finally {parcel.recycle();}
        }
        AndroidHaptics absent=new AndroidHaptics(null);
        absent.play(RiskHaptics.Cue.SINGLE);absent.play(RiskHaptics.Cue.DOUBLE);absent.cancel();
    }
}
