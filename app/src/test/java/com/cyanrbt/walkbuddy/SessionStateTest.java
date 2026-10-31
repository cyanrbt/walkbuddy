package com.cyanrbt.walkbuddy;
import org.junit.Test;
import static org.junit.Assert.*;
public class SessionStateTest {
    private SessionState live() {SessionState s=new SessionState();s.start(100);s.capture(10,150);s.display(150);s.imageChecked(s.generation,150,150);assertTrue(s.promote(150));return s;}
    @Test public void recognitionIsOffOnEveryStart() {SessionState s=live();assertFalse(s.recognition);s.recognition=true;s.stop();s.start(200);assertFalse(s.recognition);}
    @Test public void analysisDeadlineCannotBeRenewedBeforeWatchdog() {SessionState s=live();s.recognition=true;s.analysisAt=150;s.capture(20,900);s.display(900);s.capture(30,1600);s.display(1600);assertTrue(s.acceptResult(s.generation,1600,1650));assertFalse(s.acceptResult(s.generation,1600,1651));assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);}
    @Test public void captureCannotRenewAfterDisplayDeadlineBeforeWatchdog() {SessionState s=live();long g=s.generation;s.capture(20,1351);assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);assertTrue(s.generation>g);assertEquals(0,s.captureAt);}
    @Test public void displayCannotRenewAfterCaptureDeadlineBeforeWatchdog() {SessionState s=live();s.display(1351);assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);assertEquals(0,s.displayAt);}
    @Test public void startupCannotPromoteAfterDeadlineBeforeWatchdog() {SessionState s=new SessionState();s.start(100);s.capture(10,5099);s.display(5099);assertFalse(s.promote(5101));assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);}
    @Test public void validHeartbeatAtDeadlineIsAccepted() {SessionState s=live();s.capture(20,1350);s.display(1350);assertEquals(SessionState.Phase.LIVE,s.phase);assertTrue(s.fresh(1350));}
    @Test public void duplicateStartAndStopAreSafe() {SessionState s=live();long g=s.generation;assertFalse(s.start(200));assertEquals(g,s.generation);s.stop();s.stop();assertEquals(SessionState.Phase.STOPPED,s.phase);assertFalse(s.recognition);}
    @Test public void startupNeedsCaptureAndDisplay() {SessionState s=new SessionState();s.start(100);s.capture(10,200);assertFalse(s.promote(200));assertTrue(s.expired(5101));}
    @Test public void frozenSensorCannotRenewHeartbeat() {SessionState s=live();s.capture(10,2000);s.display(2000);assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);}
    @Test public void displayFreezeFailsEvenIfCameraProgresses() {SessionState s=live();s.capture(20,2000);assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);}
    @Test public void analysisTimeoutInvalidatesPreview() {SessionState s=live();s.recognition=true;s.analysisAt=150;s.capture(20,1800);s.display(1800);assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);}
    @Test public void lateAndOldGenerationResultsAreRejected() {SessionState s=live();s.recognition=true;assertTrue(s.acceptResult(s.generation,150,200));assertFalse(s.acceptResult(s.generation,150,1151));long g=s.generation;s.unavailable("故障");assertFalse(s.acceptResult(g,150,200));assertFalse(s.recognition);assertEquals(0,s.displayAt);}
    @Test public void explicitRecoveryResetsEveryHeartbeat() {SessionState s=live();s.recognition=true;s.unavailable("占用");assertTrue(s.start(500));assertFalse(s.recognition);assertFalse(s.fresh(500));assertEquals(SessionState.Phase.STARTING,s.phase);}
    @Test public void permissionDeniedDoesNotInventLiveState() {SessionState s=new SessionState();s.unavailable("拒绝");assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);assertFalse(s.fresh(200));}
}
