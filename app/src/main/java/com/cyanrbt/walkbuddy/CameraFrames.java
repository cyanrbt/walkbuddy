package com.cyanrbt.walkbuddy;

/** Validates Camera2 metadata without extending the session's existing heartbeat deadlines. */
final class CameraFrames {
    static final long MAX_AGE_NANOS=1_200_000_000L;
    // At most one 30fps frame: wait for an early result, never mark it fresh in the future.
    static final long MAX_EARLY_NANOS=33_333_334L;
    enum Decision {
        ACCEPTED(null), DEFER(null), IGNORED(null),
        EXPIRED("采集或显示截止时间已过：不可用"),
        STALE("采集时间戳过期：不可用"),
        INVALID("相机时间信息异常：不可用");
        final String failure;
        Decision(String failure){this.failure=failure;}
    }

    static Decision accept(SessionState state,long token,long stamp,long receivedAt,long now,long nowNanos,boolean realtime) {
        if(token!=state.generation || (state.phase!=SessionState.Phase.STARTING && state.phase!=SessionState.Phase.LIVE))
            return Decision.IGNORED;
        if(state.expired(now))return fail(state,Decision.EXPIRED);
        if(stamp<=0 || nowNanos<0 || receivedAt<state.startedAt || receivedAt>now)
            return fail(state,Decision.INVALID);
        if(receivedAt<state.captureAt)return Decision.IGNORED;
        if(realtime) {
            if(stamp>nowNanos) return stamp-nowNanos<=MAX_EARLY_NANOS?Decision.DEFER:fail(state,Decision.INVALID);
            if(nowNanos-stamp>MAX_AGE_NANOS)return fail(state,Decision.STALE);
        }
        if(now-receivedAt>1200)return fail(state,Decision.STALE);
        // Waiting must not make this observation newer than when the callback first arrived.
        state.capture(stamp,receivedAt);state.promote(now);
        return Decision.ACCEPTED;
    }

    static long waitMillis(long stamp,long nowNanos) {
        return Math.max(1,(stamp-nowNanos+999_999L)/1_000_000L);
    }
    private static Decision fail(SessionState state,Decision reason) {
        state.unavailable(reason.failure);return reason;
    }
}
