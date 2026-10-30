package com.cyanrbt.walkbuddy;

import android.media.AudioManager;
import android.media.ToneGenerator;

/** Created, played, stopped and released exclusively by TonePlayer's audio thread. */
final class AndroidTone implements TonePlayer.Output {
    private final ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,40);
    public boolean play(){return tone.startTone(ToneGenerator.TONE_PROP_BEEP,150);}
    public void stop(){tone.stopTone();}
    public void close(){try{tone.stopTone();}finally{tone.release();}}
}
