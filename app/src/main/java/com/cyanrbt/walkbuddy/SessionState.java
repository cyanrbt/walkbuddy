package com.cyanrbt.walkbuddy;

/** 所有状态转移在 预览 Activity 主线程；纯 Java 便于审查和单测。 */
public final class SessionState {
    public enum Phase { STOPPED, STARTING, LIVE, UNAVAILABLE }
    public Phase phase = Phase.STOPPED;
    public long generation, startedAt, captureAt, displayAt, analysisAt;
    private long checkedFrameAt;
    private long sensorTimestamp = -1;
    public boolean recognition;
    public String reason = "已停止；请主动开启";
    public boolean start(long now) {
        if (phase == Phase.STARTING || phase == Phase.LIVE) return false;
        generation++; phase = Phase.STARTING; startedAt = now;
        captureAt = displayAt = analysisAt = checkedFrameAt = 0; sensorTimestamp = -1;
        recognition = false; reason = "正在启动，尚无实时画面"; return true;
    }
    private boolean latchExpired(long now) {
        if(expired(now)){unavailable("帧或分析截止时间已过：不可用");return true;}return false;
    }
    public void capture(long timestamp, long now) {
        if(latchExpired(now))return;
        if ((phase == Phase.STARTING || phase == Phase.LIVE) && timestamp > sensorTimestamp) {
            sensorTimestamp = timestamp; captureAt = now;
        }
    }
    public void display(long now) { if(latchExpired(now))return; if (phase == Phase.STARTING || phase == Phase.LIVE) displayAt = now; }
    public boolean fresh(long now) { return captureAt > 0 && displayAt > 0 && now - captureAt <= 1200 && now - displayAt <= 1200; }
    /** Keep the checked frame's original time; delivery cannot make old pixels fresh. */
    public boolean imageChecked(long token,long frameAt,long now) {
        if(token!=generation || phase!=Phase.STARTING)return false;
        if(latchExpired(now))return false;
        if(frameAt<startedAt || frameAt<=0 || frameAt>now || now-frameAt>1200)return false;
        checkedFrameAt=Math.max(checkedFrameAt,frameAt);return true;
    }
    public boolean promote(long now) {
        if(latchExpired(now))return false;
        if (phase == Phase.STARTING && fresh(now) && checkedFrameAt>0
            && checkedFrameAt<=now && now-checkedFrameAt<=1200) {
            phase = Phase.LIVE; reason = "实时画面"; return true;
        }
        return false;
    }
    public boolean expired(long now) {
        if (phase == Phase.STARTING) return now - startedAt > 5000;
        return phase == Phase.LIVE && (!fresh(now) || (recognition && now - analysisAt > 1500));
    }
    public boolean acceptResult(long token, long frameAt, long now) {
        if(token==generation && phase==Phase.LIVE && recognition && latchExpired(now))return false;
        return token == generation && phase == Phase.LIVE && recognition && fresh(now) && now - analysisAt <= 1500 && now - frameAt <= 1000;
    }
    public void unavailable(String message) { generation++; phase = Phase.UNAVAILABLE; reason = message; recognition = false; captureAt = displayAt = analysisAt = checkedFrameAt = 0; }
    public void stop() { generation++; phase = Phase.STOPPED; recognition = false; captureAt = displayAt = analysisAt = checkedFrameAt = 0; reason = "已停止；请主动开启"; }
}
