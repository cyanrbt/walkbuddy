package com.cyanrbt.walkbuddy;

/** Only raw-depth timestamps determine depth age; confidence belongs to the same AR Frame. */
final class DepthTiming {
    enum State { FRESH, MISSING_TIMESTAMP, FUTURE_TIMESTAMP, EXPIRED }
    static State check(long frame,long depth) {
        if(frame<=0 || depth<=0)return State.MISSING_TIMESTAMP;
        if(depth>frame)return State.FUTURE_TIMESTAMP;
        return frame-depth>500000000L?State.EXPIRED:State.FRESH;
    }
}
