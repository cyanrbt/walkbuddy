package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewHealthTest {
    private SessionState live(long capture,long display) {
        SessionState state=new SessionState();state.phase=SessionState.Phase.LIVE;
        state.captureAt=capture;state.displayAt=display;state.recognition=true;state.analysisAt=100;
        return state;
    }
    @Test public void exactAnalysisBoundaryRemainsValidButOneMillisecondLateStopsDetection() {
        SessionState state=live(1600,1600);
        assertEquals(PreviewHealth.Decision.KEEP,PreviewHealth.check(state,1600));
        assertEquals(PreviewHealth.Decision.STOP_DETECTION,PreviewHealth.check(state,1601));
        assertEquals(100,state.analysisAt);assertTrue(state.recognition);
    }
    @Test public void staleCaptureTakesPriorityOverAnalysisFailure() {
        assertEquals(PreviewHealth.Decision.STOP_PREVIEW,PreviewHealth.check(live(400,1601),1601));
    }
    @Test public void staleDisplayTakesPriorityOverAnalysisFailure() {
        assertEquals(PreviewHealth.Decision.STOP_PREVIEW,PreviewHealth.check(live(1601,400),1601));
    }
    @Test public void missingHeartbeatsNeverQualifyForPreviewOnlyRecovery() {
        assertEquals(PreviewHealth.Decision.STOP_PREVIEW,PreviewHealth.check(live(0,1601),1601));
        assertEquals(PreviewHealth.Decision.STOP_PREVIEW,PreviewHealth.check(live(1601,0),1601));
    }
    @Test public void cameraDeadlineIsStill1200EvenWithDetectionOff() {
        SessionState state=live(100,100);state.recognition=false;
        assertEquals(PreviewHealth.Decision.KEEP,PreviewHealth.check(state,1300));
        assertEquals(PreviewHealth.Decision.STOP_PREVIEW,PreviewHealth.check(state,1301));
    }
    @Test public void startupKeepsItsOriginalFiveSecondDeadline() {
        SessionState state=new SessionState();state.start(100);
        assertEquals(PreviewHealth.Decision.KEEP,PreviewHealth.check(state,5100));
        assertEquals(PreviewHealth.Decision.STOP_PREVIEW,PreviewHealth.check(state,5101));
    }
}
