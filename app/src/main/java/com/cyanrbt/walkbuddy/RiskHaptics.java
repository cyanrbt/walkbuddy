package com.cyanrbt.walkbuddy;

import java.util.List;

/** Remembers only the last requested cue, so current risk can cancel a remaining pulse. */
final class RiskHaptics {
    enum Cue {
        NONE, SINGLE, DOUBLE;

        long[] fallbackTimings() {
            if(this==SINGLE)return new long[]{0,150};
            if(this==DOUBLE)return new long[]{0,100,100,150};
            return new long[0];
        }
    }
    interface Output {
        void play(Cue cue);
        void cancel();
    }
    private final Output output;
    private Detection.Risk requested=Detection.Risk.CAPTURED;

    RiskHaptics(Output output){this.output=output;}

    static Detection.Risk strongest(List<Detection> detections) {
        Detection.Risk worst=Detection.Risk.CAPTURED;
        for(Detection detection:detections)if(detection.risk.ordinal()>worst.ordinal())worst=detection.risk;
        return worst;
    }
    void alert(Detection.Risk risk) {
        if(risk==Detection.Risk.CAPTURED){update(risk);return;}
        requested=risk;
        output.play(risk==Detection.Risk.NEAR?Cue.DOUBLE:Cue.SINGLE);
    }
    /** Updating current results never starts or repeats a cue. */
    void update(Detection.Risk risk) {
        if(risk.ordinal()<requested.ordinal()) {
            requested=Detection.Risk.CAPTURED;
            output.cancel();
        }
    }
    void clear(){requested=Detection.Risk.CAPTURED;output.cancel();}
}
