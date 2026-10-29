package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static com.cyanrbt.walkbuddy.CameraFrames.Decision.*;
import static org.junit.Assert.*;

public class CameraFramesTest {
    private SessionState live() {
        SessionState s=new SessionState();s.start(1000);s.capture(1_000_000_000L,1000);s.display(1000);s.promote(1000);return s;
    }
    @Test public void earlyMetadataDoesNotRefreshUntilItsTimeActuallyArrives() {
        SessionState s=live();long token=s.generation,stamp=1_117_000_000L;
        assertEquals(DEFER,CameraFrames.accept(s,token,stamp,1100,1100,1_100_000_000L,true));
        assertEquals(1000,s.captureAt);
        assertEquals(17,CameraFrames.waitMillis(stamp,1_100_000_000L));
        assertEquals(DEFER,CameraFrames.accept(s,token,stamp,1100,1116,1_116_999_999L,true));
        assertEquals(1000,s.captureAt);
        assertEquals(ACCEPTED,CameraFrames.accept(s,token,stamp,1100,1117,stamp,true));
        assertEquals(1100,s.captureAt); // Never replace arrival time with the later validation time.
    }
    @Test public void waitingDoesNotExtendOriginalCaptureExpiry() {
        SessionState s=live();long token=s.generation;
        assertEquals(DEFER,CameraFrames.accept(s,token,2_015_000_000L,1999,1999,1_999_000_000L,true));
        assertEquals(ACCEPTED,CameraFrames.accept(s,token,2_015_000_000L,1999,2015,2_015_000_000L,true));
        s.display(2100);s.display(3000);
        assertFalse(s.expired(3199));
        assertTrue(s.expired(3200));
    }
    @Test public void aLateTimerCannotReviveAnAlreadyExpiredSession() {
        SessionState s=live();long token=s.generation;
        assertEquals(DEFER,CameraFrames.accept(s,token,1_117_000_000L,1100,1100,1_100_000_000L,true));
        assertEquals(EXPIRED,CameraFrames.accept(s,token,1_117_000_000L,1100,2201,2_201_000_000L,true));
        assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);assertEquals(0,s.captureAt);
        assertEquals(IGNORED,CameraFrames.accept(s,token,2_202_000_000L,2202,2202,2_202_000_000L,true));
    }
    @Test public void earlyResultsCannotExtendTheStartupDeadline() {
        SessionState s=new SessionState();s.start(1000);s.display(5999);long token=s.generation;
        assertEquals(DEFER,CameraFrames.accept(s,token,6_001_000_000L,5999,6000,6_000_000_000L,true));
        assertEquals(EXPIRED,CameraFrames.accept(s,token,6_001_000_000L,5999,6001,6_001_000_000L,true));
        assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);
    }
    @Test public void futureWaitBudgetIsBoundedToOneFrame() {
        SessionState s=live();long now=1_100_000_000L;
        assertEquals(DEFER,CameraFrames.accept(s,s.generation,now+CameraFrames.MAX_EARLY_NANOS,1100,1100,now,true));
        assertEquals(INVALID,CameraFrames.accept(s,s.generation,now+CameraFrames.MAX_EARLY_NANOS+1,1100,1100,now,true));
        assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);
    }
    @Test public void theExistingTwelveHundredLimitStillHasANanosecondBoundary() {
        SessionState allowed=new SessionState();allowed.start(1000);allowed.capture(1,1000);allowed.display(1000);allowed.promote(1000);
        assertEquals(ACCEPTED,CameraFrames.accept(allowed,allowed.generation,1_000_000_000L,2200,2200,2_200_000_000L,true));
        SessionState late=live();
        assertEquals(STALE,CameraFrames.accept(late,late.generation,1_000_000_000L,2200,2200,2_200_000_001L,true));
        assertEquals(SessionState.Phase.UNAVAILABLE,late.phase);
    }
    @Test public void stoppedAndReplacedSessionsIgnoreDeferredCallbacks() {
        SessionState s=live();long old=s.generation;s.stop();
        assertEquals(IGNORED,CameraFrames.accept(s,old,1_117_000_000L,1100,1117,1_117_000_000L,true));
        s.start(1200);
        assertEquals(IGNORED,CameraFrames.accept(s,old,1_300_000_000L,1300,1300,1_300_000_000L,true));
        assertEquals(0,s.captureAt);assertEquals(SessionState.Phase.STARTING,s.phase);
    }
    @Test public void anOlderArrivalNeverMovesTheHeartbeatBackwards() {
        SessionState s=live();long token=s.generation;
        CameraFrames.accept(s,token,1_110_000_000L,1110,1110,1_110_000_000L,true);
        assertEquals(IGNORED,CameraFrames.accept(s,token,1_117_000_000L,1100,1117,1_117_000_000L,true));
        assertEquals(1110,s.captureAt);
    }
    @Test public void unknownTimestampSourceUsesProgressNotAnAssumedClockOrigin() {
        SessionState s=new SessionState();s.start(1000);s.capture(100,1000);s.display(1000);s.promote(1000);
        assertEquals(ACCEPTED,CameraFrames.accept(s,s.generation,101,1100,1100,1_100_000_000L,false));
        assertEquals(1100,s.captureAt);
        assertEquals(ACCEPTED,CameraFrames.accept(s,s.generation,101,1200,1200,1_200_000_000L,false));
        assertEquals(1100,s.captureAt); // Duplicate timestamp never renews the capture heartbeat.
    }
    @Test public void freshCameraMetadataCannotRescueExpiredAnalysis() {
        SessionState s=live();s.capture(2_000_000_000L,2000);s.display(2000);
        s.capture(2_400_000_000L,2400);s.display(2400);s.recognition=true;s.analysisAt=1000;
        assertEquals(EXPIRED,CameraFrames.accept(s,s.generation,2_501_000_000L,2501,2501,2_501_000_000L,true));
        assertFalse(s.recognition);assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);
    }
    @Test public void missingOrImpossibleArrivalTimesFailClosed() {
        for(long stamp:new long[]{0,-1,Long.MAX_VALUE}) {
            SessionState s=live();assertEquals(INVALID,CameraFrames.accept(s,s.generation,stamp,1100,1100,1_100_000_000L,true));
        }
        SessionState s=live();assertEquals(INVALID,CameraFrames.accept(s,s.generation,1_100_000_000L,1101,1100,1_100_000_000L,true));
    }
    @Test public void fractionalWaitRoundsUpRatherThanAcceptingBeforeTheTimestamp() {
        assertEquals(1,CameraFrames.waitMillis(1_100_000_001L,1_100_000_000L));
        assertEquals(2,CameraFrames.waitMillis(1_101_000_001L,1_100_000_000L));
    }
}
