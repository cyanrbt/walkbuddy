package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecoveryGuideTest {
    @Test public void missingPermissionAlwaysLeadsToSettingsInsteadOfRetryLoop() {
        for(RecoveryGuide.Reason reason:RecoveryGuide.Reason.values())
            assertEquals(RecoveryGuide.Action.SETTINGS,RecoveryGuide.resolve(reason,false).action);
    }
    @Test public void returningWithPermissionRequiresAnExplicitRetry() {
        assertEquals(RecoveryGuide.Action.RETRY,RecoveryGuide.resolve(RecoveryGuide.Reason.PERMISSION,true).action);
    }
    @Test public void unavailableHardwareDoesNotOfferEndlessRetry() {
        assertEquals(RecoveryGuide.Action.HOME,RecoveryGuide.resolve(RecoveryGuide.Reason.UNSUPPORTED,true).action);
    }
    @Test public void unavailableEnhancementOrModelCanReturnToBasicPreview() {
        for(RecoveryGuide.Reason reason:new RecoveryGuide.Reason[]{RecoveryGuide.Reason.DEPTH,RecoveryGuide.Reason.MODEL})
            assertEquals(RecoveryGuide.Action.BASIC,RecoveryGuide.resolve(reason,true).action);
    }
    @Test public void transientProblemsOfferManualRecovery() {
        for(RecoveryGuide.Reason reason:new RecoveryGuide.Reason[]{RecoveryGuide.Reason.GENERAL,
            RecoveryGuide.Reason.DARK,RecoveryGuide.Reason.CAMERA_BUSY,RecoveryGuide.Reason.LOCKED,RecoveryGuide.Reason.HIDDEN})
            assertEquals(RecoveryGuide.Action.RETRY,RecoveryGuide.resolve(reason,true).action);
    }
    @Test public void installationWaitCannotStartAnotherCameraSession() {
        assertEquals(RecoveryGuide.Action.WAIT,RecoveryGuide.resolve(RecoveryGuide.Reason.INSTALLING,true).action);
        assertEquals(RecoveryGuide.Action.WAIT,RecoveryGuide.resolve(RecoveryGuide.Reason.SWITCHING,true).action);
    }
}
