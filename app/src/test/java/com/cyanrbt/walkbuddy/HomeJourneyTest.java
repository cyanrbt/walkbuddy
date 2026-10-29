package com.cyanrbt.walkbuddy;

import org.junit.Test;

import static com.cyanrbt.walkbuddy.HomeJourney.Hint.*;
import static com.cyanrbt.walkbuddy.HomeJourney.Step.*;
import static org.junit.Assert.*;

public class HomeJourneyTest {
    @Test public void availablePipIsNotACompletedExperience() {
        HomeJourney first = HomeJourney.resolve(false, false, false, true, true);
        assertEquals(CURRENT, first.camera);
        assertEquals(UPCOMING, first.preview);
        assertEquals(UPCOMING, first.pip);
        assertEquals(CAMERA, first.hint);

        HomeJourney granted = HomeJourney.resolve(true, false, false, true, true);
        assertEquals(DONE, granted.camera);
        assertEquals(CURRENT, granted.preview);
        assertEquals(UPCOMING, granted.pip);
        assertEquals(PREVIEW, granted.hint);
    }

    @Test public void livePreviewLeadsToPipUntilActuallyEntered() {
        HomeJourney preview = HomeJourney.resolve(true, true, false, true, true);
        assertEquals(DONE, preview.preview);
        assertEquals(CURRENT, preview.pip);
        assertEquals(PIP, preview.hint);

        HomeJourney used = HomeJourney.resolve(true, true, true, true, true);
        assertEquals(DONE, used.pip);
        assertEquals(READY, used.hint);
    }

    @Test public void cameraRevocationTakesPriorityOverSavedHistory() {
        HomeJourney revoked = HomeJourney.resolve(false, true, true, true, true);
        assertEquals(CURRENT, revoked.camera);
        assertEquals(DONE, revoked.preview);
        assertEquals(CAMERA, revoked.hint);
    }

    @Test public void disabledPipDoesNotInvalidateBasicPreview() {
        HomeJourney disabled = HomeJourney.resolve(true, true, false, true, false);
        assertEquals(DONE, disabled.preview);
        assertEquals(CURRENT, disabled.pip);
        assertEquals(PIP_DISABLED, disabled.hint);
    }

    @Test public void disablingPipAfterUseStillExplainsCurrentBlocker() {
        HomeJourney disabled = HomeJourney.resolve(true, true, true, true, false);
        assertEquals(CURRENT, disabled.pip);
        assertEquals(PIP_DISABLED, disabled.hint);
        HomeJourney restored = HomeJourney.resolve(true, true, true, true, true);
        assertEquals(READY, restored.hint);
    }

    @Test public void unsupportedPipIsOptionalRatherThanAnImpossibleTask() {
        HomeJourney basic = HomeJourney.resolve(true, true, false, false, false);
        assertEquals(DONE, basic.preview);
        assertEquals(OPTIONAL, basic.pip);
        assertEquals(IN_APP, basic.hint);
        HomeJourney first = HomeJourney.resolve(true, false, false, false, false);
        assertEquals(PREVIEW, first.hint);
        assertEquals(OPTIONAL, first.pip);
    }

    @Test public void allInputCombinationsRespectPermissionAndEvidence() {
        for (int bits = 0; bits < 32; bits++) {
            boolean camera = (bits & 1) != 0, preview = (bits & 2) != 0, entered = (bits & 4) != 0;
            boolean supported = (bits & 8) != 0, available = (bits & 16) != 0;
            HomeJourney journey = HomeJourney.resolve(camera, preview, entered, supported, available);
            assertEquals(camera, journey.camera == DONE);
            assertEquals(preview, journey.preview == DONE);
            assertEquals(supported && available && entered, journey.pip == DONE);
            assertEquals(!supported, journey.pip == OPTIONAL);
            if (!camera) assertEquals(CAMERA, journey.hint);
            else if (!preview) assertEquals(PREVIEW, journey.hint);
            else if (journey.hint == READY) assertTrue(supported && available && entered);
        }
    }
}
