package com.cyanrbt.walkbuddy;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class PipMenuTest {
    @Test public void cameraStopAlwaysRemainsFirstAtEveryCapacity() {
        for(int slots=-1;slots<=6;slots++)for(SessionState.Phase phase:SessionState.Phase.values())
            for(boolean detection:new boolean[]{false,true})for(boolean sound:new boolean[]{false,true})
                assertEquals(PipMenu.Item.STOP_PREVIEW,PipMenu.items(slots,phase,detection,sound).get(0));
    }
    @Test public void oneSlotPreservesStoppingTheCameraEvenWithSoundEnabled() {
        assertEquals(Arrays.asList(PipMenu.Item.STOP_PREVIEW),PipMenu.items(1,SessionState.Phase.LIVE,true,true));
    }
    @Test public void twoSlotsRetainBothExistingStopControls() {
        for(boolean sound:new boolean[]{false,true})assertEquals(Arrays.asList(PipMenu.Item.STOP_PREVIEW,PipMenu.Item.STOP_DETECTION),
            PipMenu.items(2,SessionState.Phase.LIVE,true,sound));
    }
    @Test public void thirdSlotOffersTheExplicitOppositeSoundChoice() {
        assertEquals(Arrays.asList(PipMenu.Item.STOP_PREVIEW,PipMenu.Item.STOP_DETECTION,PipMenu.Item.ENABLE_SOUND),
            PipMenu.items(3,SessionState.Phase.LIVE,true,false));
        assertEquals(Arrays.asList(PipMenu.Item.STOP_PREVIEW,PipMenu.Item.STOP_DETECTION,PipMenu.Item.DISABLE_SOUND),
            PipMenu.items(3,SessionState.Phase.LIVE,true,true));
    }
    @Test public void stoppedStartingUnavailableAndDetectionOffDoNotOfferSound() {
        for(SessionState.Phase phase:SessionState.Phase.values())for(boolean detection:new boolean[]{false,true}) {
            if(phase==SessionState.Phase.LIVE && detection)continue;
            assertEquals(Arrays.asList(PipMenu.Item.STOP_PREVIEW),PipMenu.items(6,phase,detection,true));
        }
    }
    @Test public void largerCapacityDoesNotDuplicateOrInventMoreControls() {
        for(int capacity=1;capacity<=10;capacity++) {
            List<PipMenu.Item> items=PipMenu.items(capacity,SessionState.Phase.LIVE,true,true);
            assertEquals(Math.min(capacity,3),items.size());
            assertEquals(items.size(),new java.util.HashSet<>(items).size());
        }
    }
}
