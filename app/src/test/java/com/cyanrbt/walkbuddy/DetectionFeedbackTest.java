package com.cyanrbt.walkbuddy;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DetectionFeedbackTest {
    private List<Detection> person() {
        return Collections.singletonList(Detection.fromModel("人",0.8f,new float[]{0.1f,0.3f,0.9f,0.7f},320,320,0,0));
    }
    private DepthSnapshot near(long at) {
        return new DepthSnapshot(at,Collections.singletonList(new Detection("空间障碍",1,.3f,.1f,.7f,.9f,Detection.Risk.NEAR,.7f,null)),"深度有效",
            DepthSnapshot.Stage.MEASURING,true,true);
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
        f.depth(new DepthSnapshot(300,Collections.emptyList(),"深度更新较慢",
            DepthSnapshot.Stage.DEPTH_STALE,false,false),0,300);
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
    @Test public void repeatedWatchdogChecksReuseTheSameVisibleSnapshot() {
        DetectionFeedback f=new DetectionFeedback();f.basic(person(),100,100);
        AlertPolicy.Decision first=f.visible(100);
        for(long now=101;now<=1100;now++)assertSame(first,f.visible(now));
        assertFalse(first.alert);
        AlertPolicy.Decision expired=f.visible(1101);
        assertNotSame(first,expired);assertTrue(expired.detections.isEmpty());
        assertSame(expired,f.visible(1500));
    }
    @Test public void cachedCombinedSnapshotExpiresEachSourceAtItsOwnBoundary() {
        DetectionFeedback f=new DetectionFeedback();
        f.basic(person(),100,100);f.basic(person(),600,600);
        f.depth(near(300),0,300);f.depth(near(600),0,600);
        AlertPolicy.Decision both=f.visible(600);
        assertEquals(2,both.detections.size());assertTrue(both.message.contains("请及时注意"));
        assertSame(both,f.visible(1100));
        AlertPolicy.Decision basicOnly=f.visible(1101);
        assertEquals(1,basicOnly.detections.size());assertTrue(basicOnly.message.contains("未测距"));
        assertSame(basicOnly,f.visible(1600));
        assertTrue(f.visible(1601).detections.isEmpty());
        // An earlier immutable decision must not be changed by later expiry.
        assertEquals(2,both.detections.size());
    }
    @Test public void newConfirmationAndGeometryCannotBeHiddenByCache() {
        DetectionFeedback f=new DetectionFeedback();f.basic(person(),100,100);
        AlertPolicy.Decision gray=f.visible(100);
        f.basic(person(),600,600);
        AlertPolicy.Decision yellow=f.visible(600);
        assertNotSame(gray,yellow);
        assertEquals(Detection.Risk.CAPTURED,gray.detections.get(0).risk);
        assertEquals(Detection.Risk.CAUTION,yellow.detections.get(0).risk);
        Detection side=new Detection("人",.8f,.01f,.1f,.2f,.9f);
        f.basic(Collections.singletonList(side),800,800);
        AlertPolicy.Decision moved=f.visible(800);
        assertNotSame(yellow,moved);assertEquals(.01f,moved.detections.get(0).left,0f);
    }
    @Test public void clearDropsCachedDataAndDoesNotReplayAlerts() {
        DetectionFeedback f=new DetectionFeedback();f.depth(near(100),0,100);f.depth(near(400),0,400);
        AlertPolicy.Decision red=f.visible(400);
        f.clear();AlertPolicy.Decision empty=f.visible(401);
        assertNotSame(red,empty);assertTrue(empty.detections.isEmpty());assertFalse(empty.alert);
        assertFalse(f.depth(near(600),0,600));
        assertEquals(Detection.Risk.CAPTURED,f.visible(600).detections.get(0).risk);
    }
    @Test public void invalidDepthImmediatelyRemovesCachedContours() {
        DetectionFeedback f=new DetectionFeedback();f.basic(person(),100,100);f.depth(near(200),0,200);
        AlertPolicy.Decision combined=f.visible(200);
        assertEquals(2,combined.detections.size());
        f.depth(null,0,201);
        AlertPolicy.Decision basicOnly=f.visible(201);
        assertNotSame(combined,basicOnly);assertEquals(1,basicOnly.detections.size());
    }
    @Test public void resultsFromTheFutureAreNotDisplayed() {
        DetectionFeedback f=new DetectionFeedback();f.basic(person(),200,200);f.depth(near(200),0,200);
        assertEquals(2,f.visible(200).detections.size());
        assertTrue(f.visible(199).detections.isEmpty());
    }
}
