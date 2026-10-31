package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;

/** Readiness requires fresh capture, display and a checked image from the current observation. */
public class PreviewReadinessTest {
    private SessionState starting() {
        SessionState s=new SessionState();s.start(1000);s.capture(1,1100);s.display(1100);return s;
    }
    @Test public void framesAloneCannotExposeAnUncheckedImage() {
        SessionState s=starting();assertFalse(s.promote(1100));assertEquals(SessionState.Phase.STARTING,s.phase);
        assertTrue(s.imageChecked(s.generation,1100,1150));assertTrue(s.promote(1150));
    }
    @Test public void validationAloneCannotInventCaptureOrDisplay() {
        for(boolean capture:new boolean[]{false,true}) {
            SessionState s=new SessionState();s.start(1000);
            if(capture)s.capture(1,1100);else s.display(1100);
            assertTrue(s.imageChecked(s.generation,1100,1150));assertFalse(s.promote(1150));
        }
    }
    @Test public void checkedFrameAtExactDeadlineIsValidAndOneMillisecondLaterIsNot() {
        SessionState s=starting();s.capture(2,2200);s.display(2200);
        assertTrue(s.imageChecked(s.generation,1000,2200));assertTrue(s.promote(2200));
        s=starting();s.capture(2,2201);s.display(2201);
        assertFalse(s.imageChecked(s.generation,1000,2201));assertFalse(s.promote(2201));
    }
    @Test public void laterHeartbeatsCannotRenewAnOldImageCheck() {
        SessionState s=starting();assertTrue(s.imageChecked(s.generation,1100,1100));
        s.capture(2,2301);s.display(2301);assertFalse(s.promote(2301));
        assertTrue(s.imageChecked(s.generation,2301,2301));assertTrue(s.promote(2301));
    }
    @Test public void deliveryTimeCannotReplaceTheSampleTime() {
        SessionState s=starting();assertTrue(s.imageChecked(s.generation,1100,2299));
        s.capture(2,2301);s.display(2301);assertFalse(s.promote(2301));
    }
    @Test public void futureOrPreviousObservationImagesCannotQualify() {
        SessionState s=starting();
        for(long at:new long[]{-1,0,999,1151,Long.MAX_VALUE})assertFalse(s.imageChecked(s.generation,at,1150));
        assertFalse(s.promote(1150));
    }
    @Test public void startupDeadlineIncludesTheFirstImageCheck() {
        SessionState s=starting();s.capture(2,6000);s.display(6000);
        assertTrue(s.imageChecked(s.generation,6000,6000));assertTrue(s.promote(6000));
        s=starting();long token=s.generation;s.capture(2,5999);s.display(5999);
        assertFalse(s.imageChecked(token,5999,6001));assertEquals(SessionState.Phase.UNAVAILABLE,s.phase);
    }
    @Test public void anOldGenerationCannotExpireOrPromoteANewStartup() {
        SessionState s=starting();long old=s.generation;s.stop();s.start(2000);
        assertFalse(s.imageChecked(old,2100,99999));assertEquals(SessionState.Phase.STARTING,s.phase);
        s.capture(2,2100);s.display(2100);assertFalse(s.promote(2100));
    }
    @Test public void explicitRestartRequiresItsOwnCheckedImage() {
        SessionState s=starting();s.imageChecked(s.generation,1100,1100);assertTrue(s.promote(1100));
        s.unavailable("test");s.start(2000);s.capture(2,2100);s.display(2100);assertFalse(s.promote(2100));
        s.imageChecked(s.generation,2100,2100);assertTrue(s.promote(2100));
    }
    @Test public void inactiveSessionsIgnoreImageChecks() {
        SessionState s=new SessionState();assertFalse(s.imageChecked(s.generation,1000,1000));
        s.start(1000);s.stop();assertFalse(s.imageChecked(s.generation,1001,1001));
        s.unavailable("test");assertFalse(s.imageChecked(s.generation,1002,1002));
    }
    @Test public void aCheckedImageDoesNotChangeTheLiveFrameDeadline() {
        SessionState s=starting();s.imageChecked(s.generation,1100,1100);assertTrue(s.promote(1100));
        assertFalse(s.expired(2300));assertTrue(s.expired(2301));
    }
}
