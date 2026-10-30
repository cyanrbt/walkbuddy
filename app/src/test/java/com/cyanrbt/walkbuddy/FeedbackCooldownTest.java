package com.cyanrbt.walkbuddy;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedbackCooldownTest {
    private static java.util.List<Detection> basic() {
        return Collections.singletonList(Detection.fromModel("人",.8f,
            new float[]{.1f,.3f,.9f,.7f},320,320,0,0));
    }
    private static DepthSnapshot depth(long at,Detection.Risk risk,float meters) {
        return new DepthSnapshot(at,Collections.singletonList(new Detection("空间障碍",1,
            .3f,.1f,.7f,.9f,risk,meters,null)),"深度有效",DepthSnapshot.Stage.MEASURING,true,true);
    }
    private static boolean near(DetectionFeedback feedback,long at) {
        return feedback.depth(depth(at,Detection.Risk.NEAR,.7f),0,at);
    }
    private static DetectionFeedback blockedNear() {
        DetectionFeedback feedback=new DetectionFeedback();
        assertFalse(feedback.basic(basic(),100,100));
        assertTrue(feedback.basic(basic(),600,600));
        assertFalse(near(feedback,700));
        assertFalse(near(feedback,900));
        assertEquals(Detection.Risk.NEAR,feedback.visible(900).detections.get(1).risk);
        return feedback;
    }

    @Test public void confirmedNearSurvivesTheSharedCooldownAndUsesCurrentDistance() {
        DetectionFeedback feedback=blockedNear();
        assertFalse(near(feedback,1100));assertFalse(near(feedback,1599));
        assertTrue(feedback.depth(depth(1600,Detection.Risk.NEAR,.6f),0,1600));
        assertTrue(feedback.visible(1600).message.startsWith("约 0.6 米"));
        assertTrue(feedback.visible(1600).message.contains("请及时注意"));
    }

    @Test public void secondSourceCautionIsNotConsumedBeforeItCanBeDelivered() {
        DetectionFeedback feedback=new DetectionFeedback();
        assertFalse(feedback.depth(depth(100,Detection.Risk.CAUTION,1.5f),0,100));
        assertTrue(feedback.depth(depth(400,Detection.Risk.CAUTION,1.5f),0,400));
        assertFalse(feedback.basic(basic(),500,500));
        for(long at:new long[]{700,1200,1700,2200,2700,3399})
            assertFalse(feedback.basic(basic(),at,at));
        assertTrue(feedback.basic(basic(),3400,3400));
        assertTrue(feedback.visible(3400).message.contains("未测距"));
    }

    @Test public void deliveryStartsTheExistingRepeatCooldown() {
        DetectionFeedback feedback=blockedNear();
        assertTrue(near(feedback,1600));
        for(long at=2100;at<5600;at+=500)assertFalse(near(feedback,at));
        assertTrue(near(feedback,5600));
        assertFalse(near(feedback,5601));
    }

    @Test public void disappearingTargetCannotReleaseAnOldReminder() {
        DetectionFeedback feedback=blockedNear();
        assertFalse(feedback.depth(new DepthSnapshot(1000,Collections.emptyList(),"无目标",
            DepthSnapshot.Stage.MEASURING,true,true),0,1000));
        assertTrue(feedback.visible(1601).detections.isEmpty());
        assertFalse(near(feedback,1601));
        assertEquals(Detection.Risk.CAPTURED,feedback.visible(1601).detections.get(0).risk);
        assertTrue(near(feedback,1900));
    }

    @Test public void clearRequiresNewConfirmationAndPreservesSharedQuietTime() {
        DetectionFeedback feedback=blockedNear();feedback.clear();
        assertTrue(feedback.visible(1000).detections.isEmpty());
        assertFalse(near(feedback,1100));assertFalse(near(feedback,1200));
        assertFalse(near(feedback,1599));assertTrue(near(feedback,1600));
    }

    @Test public void duplicateStaleAndFutureSnapshotsCannotReleaseADeferredAlert() {
        DetectionFeedback feedback=blockedNear();
        assertFalse(feedback.depth(depth(900,Detection.Risk.NEAR,.7f),0,1000));
        assertFalse(feedback.depth(depth(900,Detection.Risk.NEAR,.7f),0,1600));
        assertFalse(feedback.depth(depth(1800,Detection.Risk.NEAR,.7f),0,1600));
        assertFalse(near(feedback,1600));assertTrue(near(feedback,1800));
    }

    @Test public void downgradeWaitsForCautionTimingAndNeverReplaysRed() {
        DetectionFeedback feedback=blockedNear();
        for(long at:new long[]{1100,1600,2100,2600,3100,3599})
            assertFalse(feedback.depth(depth(at,Detection.Risk.CAUTION,1.5f),0,at));
        assertTrue(feedback.depth(depth(3600,Detection.Risk.CAUTION,1.4f),0,3600));
        assertTrue(feedback.visible(3600).message.startsWith("约 1.4 米"));
        assertFalse(feedback.visible(3600).message.contains("请及时注意"));
        assertEquals(Detection.Risk.CAUTION,feedback.visible(3600).detections.get(0).risk);
    }
}
