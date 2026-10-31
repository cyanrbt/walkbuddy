package com.cyanrbt.walkbuddy;

/** Camera freshness and the user's detection choice describe different capabilities. */
final class PreviewStatus {
    enum Heading {IDLE,STARTING,PREVIEW_ONLY,LIVE}
    static Heading heading(SessionState.Phase phase,boolean fresh,boolean recognition) {
        if(phase==SessionState.Phase.STARTING)return Heading.STARTING;
        if(phase!=SessionState.Phase.LIVE || !fresh)return Heading.IDLE;
        return recognition?Heading.LIVE:Heading.PREVIEW_ONLY;
    }
    static boolean showNotice(boolean live,boolean recognition,boolean stoppedNotice,boolean compact) {
        return showNotice(live,recognition,stoppedNotice,compact,false);
    }
    static boolean showNotice(boolean live,boolean recognition,boolean stoppedNotice,boolean compact,boolean enlarged) {
        return live && !recognition && (stoppedNotice || compact || enlarged);
    }
}
