package com.cyanrbt.walkbuddy;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DetectionFeedbackTest {
    private List<Detection> person() {
        return Collections.singletonList(Detection.fromModel("人",0.8f,new float[]{0.1f,0.3f,0.9f,0.7f},320,320,0,0));
    }
    private DepthSnapshot near(long at) {
        return new DepthSnapshot(at,Collections.singletonList(new Detection("空间障碍",1,.3f,.1f,.7f,.9f,Detection.Risk.NEAR,.7f,null)),"深度有效");
    }
    @Test public void basicAttentionReturnsYellowAndVibrationWithoutInventingDistance() {
        DetectionFeedback f=new DetectionFeedback();
        assertFalse(f.basic(person(),100,100));assertTrue(f.basic(person(),600,600));
        AlertPolicy.Decision view=f.visible(600);
        assertEquals(Detection.Risk.CAUTION,view.detections.get(0).risk);
        assertTrue(view.message.contains("画面关注"));assertTrue(view.message.contains("未测距"));
        assertTrue(Float.isNaN(view.detections.get(0).meters));
    }
    @Test public void unavailableDepthDoesNotEraseOrDisableBasicDetection() {
        DetectionFeedback f=new DetectionFeedback();f.basic(person(),100,100);
        f.depth(new DepthSnapshot(300,Collections.emptyList(),"深度更新较慢"),0,300);
        assertTrue(f.basic(person(),600,600));
        f.depth(null,0,700);
        assertEquals(Detection.Risk.CAUTION,f.visible(700).detections.get(0).risk);
    }
    @Test public void sideAndLowConfidenceObjectsStayGray() {
        assertEquals(Detection.Risk.CAPTURED,Detection.fromModel("人",.9f,new float[]{0,.01f,.9f,.2f},320,320,0,0).risk);
        assertEquals(Detection.Risk.CAPTURED,Detection.fromModel("人",.4f,new float[]{0,.3f,.9f,.7f},320,320,0,0).risk);
    }
    @Test public void duplicateDepthCannotConfirmRedEvenWhileRgbContinues() {
        DetectionFeedback f=new DetectionFeedback();assertFalse(f.depth(near(100),0,100));
        f.basic(person(),200,200);assertFalse(f.depth(near(100),0,300));
        assertFalse(f.visible(300).detections.stream().anyMatch(d->d.risk==Detection.Risk.NEAR));
        assertTrue(f.depth(near(400),0,400));
        assertTrue(f.visible(400).detections.stream().anyMatch(d->d.risk==Detection.Risk.NEAR));
    }
    @Test public void staleRedDisappearsWhileNewBasicResultRemains() {
        DetectionFeedback f=new DetectionFeedback();f.depth(near(100),0,100);f.depth(near(400),0,400);
        f.basic(person(),800,800);
        assertEquals(1,f.visible(901).detections.size());
        assertEquals(Detection.Risk.CAPTURED,f.visible(901).detections.get(0).risk);
        assertTrue(f.visible(1801).detections.isEmpty());
    }
    @Test public void stoppingRequiresNewConfirmation() {
        DetectionFeedback f=new DetectionFeedback();f.basic(person(),100,100);f.basic(person(),600,600);f.clear();
        assertTrue(f.visible(700).detections.isEmpty());
        f.basic(person(),800,800);assertEquals(Detection.Risk.CAPTURED,f.visible(800).detections.get(0).risk);
    }
    @Test public void actualDepthAgeStillRejectsOldMissingAndFutureData() {
        assertEquals(DepthTiming.State.FRESH,DepthTiming.check(1000000000L,900000000L));
        assertEquals(DepthTiming.State.EXPIRED,DepthTiming.check(2000000000L,900000000L));
        assertEquals(DepthTiming.State.FUTURE_TIMESTAMP,DepthTiming.check(900000000L,1000000000L));
        assertEquals(DepthTiming.State.MISSING_TIMESTAMP,DepthTiming.check(1000000000L,0));
    }
}
