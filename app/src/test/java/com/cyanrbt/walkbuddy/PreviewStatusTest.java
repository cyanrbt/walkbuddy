package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewStatusTest {
    @Test public void enlargedDefaultPreviewKeepsDetectionOffVisibleWhenTheSettingsAreHidden() {
        assertTrue(PreviewStatus.showNotice(true,false,false,false,true));
        assertFalse(PreviewStatus.showNotice(true,false,false,false,false));
    }
    @Test public void enlargedDetectionKeepsTheLiveHeadingAndRetiresTheOffNotice() {
        assertEquals(PreviewStatus.Heading.LIVE,PreviewStatus.heading(SessionState.Phase.LIVE,true,true));
        assertFalse(PreviewStatus.showNotice(true,true,true,false,true));
    }
    @Test public void stoppedOrExpiredEnlargedViewDoesNotClaimAHealthyPreview() {
        assertFalse(PreviewStatus.showNotice(false,false,true,false,true));
        assertEquals(PreviewStatus.Heading.IDLE,PreviewStatus.heading(SessionState.Phase.UNAVAILABLE,true,false));
    }
    @Test public void freshCameraWithDetectionOffIsPreviewOnlyFromTheFirstSession() {
        assertEquals(PreviewStatus.Heading.PREVIEW_ONLY,PreviewStatus.heading(SessionState.Phase.LIVE,true,false));
        assertFalse(PreviewStatus.showNotice(true,false,false,false));
        assertTrue(PreviewStatus.showNotice(true,false,false,true));
    }
    @Test public void expandingDefaultPreviewHidesTheRedundantNoticeWithoutChangingItsHeading() {
        assertTrue(PreviewStatus.showNotice(true,false,false,true));
        assertFalse(PreviewStatus.showNotice(true,false,false,false));
        assertEquals(PreviewStatus.Heading.PREVIEW_ONLY,PreviewStatus.heading(SessionState.Phase.LIVE,true,false));
    }
    @Test public void existingDetectionStopNoticeRemainsVisibleInBothPresentations() {
        for(boolean compact:new boolean[]{false,true})assertTrue(PreviewStatus.showNotice(true,false,true,compact));
    }
    @Test public void enablingDetectionRetiresThePreviewOnlyStatusEvenIfAnOldNoticeFlagRemains() {
        assertEquals(PreviewStatus.Heading.LIVE,PreviewStatus.heading(SessionState.Phase.LIVE,true,true));
        for(boolean compact:new boolean[]{false,true})for(boolean notice:new boolean[]{false,true})
            assertFalse(PreviewStatus.showNotice(true,true,notice,compact));
    }
    @Test public void startingAndUnavailableCamerasDoNotClaimLivePreview() {
        assertEquals(PreviewStatus.Heading.STARTING,PreviewStatus.heading(SessionState.Phase.STARTING,false,false));
        for(SessionState.Phase phase:new SessionState.Phase[]{SessionState.Phase.STOPPED,SessionState.Phase.UNAVAILABLE})
            for(boolean detection:new boolean[]{false,true})assertEquals(PreviewStatus.Heading.IDLE,PreviewStatus.heading(phase,true,detection));
        assertFalse(PreviewStatus.showNotice(false,false,true,true));
    }
    @Test public void expiredLivePhaseDoesNotKeepAHealthyLookingLabelOrNotice() {
        assertEquals(PreviewStatus.Heading.IDLE,PreviewStatus.heading(SessionState.Phase.LIVE,false,false));
        assertEquals(PreviewStatus.Heading.IDLE,PreviewStatus.heading(SessionState.Phase.LIVE,false,true));
        assertFalse(PreviewStatus.showNotice(false,false,false,true));
    }
}
