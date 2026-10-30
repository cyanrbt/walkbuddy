package com.cyanrbt.walkbuddy;

/** A failed optional detector may stop only detection when the camera is still fresh. */
final class PreviewHealth {
    enum Decision { KEEP, STOP_DETECTION, STOP_PREVIEW }
    static Decision check(SessionState state,long now) {
        if(!state.expired(now))return Decision.KEEP;
        if(state.phase==SessionState.Phase.LIVE && state.fresh(now) && state.recognition
            && now-state.analysisAt>1500)return Decision.STOP_DETECTION;
        return Decision.STOP_PREVIEW;
    }
}
