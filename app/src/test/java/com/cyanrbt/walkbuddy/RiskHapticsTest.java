package com.cyanrbt.walkbuddy;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RiskHapticsTest {
    private static final class Output implements RiskHaptics.Output {
        final List<RiskHaptics.Cue> cues=new ArrayList<>();int cancels;
        public void play(RiskHaptics.Cue cue){cues.add(cue);}
        public void cancel(){cancels++;}
    }
    private static Detection detection(Detection.Risk risk) {
        return new Detection("构造目标",1,.3f,.1f,.7f,.9f,risk,risk==Detection.Risk.NEAR?.7f:1.5f,null);
    }
    @Test public void strongestConfirmedRiskWinsInEveryInputOrder() {
        Detection gray=detection(Detection.Risk.CAPTURED),yellow=detection(Detection.Risk.CAUTION),red=detection(Detection.Risk.NEAR);
        assertEquals(Detection.Risk.CAPTURED,RiskHaptics.strongest(Collections.emptyList()));
        assertEquals(Detection.Risk.CAPTURED,RiskHaptics.strongest(Collections.singletonList(gray)));
        assertEquals(Detection.Risk.CAUTION,RiskHaptics.strongest(Arrays.asList(yellow,gray)));
        for(List<Detection> rows:Arrays.asList(Arrays.asList(red,yellow,gray),Arrays.asList(gray,red,yellow),Arrays.asList(yellow,gray,red)))
            assertEquals(Detection.Risk.NEAR,RiskHaptics.strongest(rows));
    }
    @Test public void onlyAllowedReminderCallsPlayAndRiskSelectsTheCue() {
        Output output=new Output();RiskHaptics player=new RiskHaptics(output);
        for(Detection.Risk risk:Detection.Risk.values())player.update(risk);
        player.alert(Detection.Risk.CAPTURED);assertTrue(output.cues.isEmpty());
        player.alert(Detection.Risk.CAUTION);player.alert(Detection.Risk.NEAR);
        assertEquals(Arrays.asList(RiskHaptics.Cue.SINGLE,RiskHaptics.Cue.DOUBLE),output.cues);
        for(int i=0;i<100;i++)player.update(Detection.Risk.NEAR);
        assertEquals(2,output.cues.size());assertEquals(0,output.cancels);
    }
    @Test public void downgradeCancelsTheRemainingDoubleWithoutStartingASingle() {
        Output output=new Output();RiskHaptics player=new RiskHaptics(output);
        player.alert(Detection.Risk.NEAR);player.update(Detection.Risk.CAUTION);
        for(int i=0;i<100;i++)player.update(Detection.Risk.CAUTION);
        assertEquals(1,output.cancels);assertEquals(Collections.singletonList(RiskHaptics.Cue.DOUBLE),output.cues);
        player.alert(Detection.Risk.CAUTION);assertEquals(RiskHaptics.Cue.SINGLE,output.cues.get(1));
    }
    @Test public void disappearanceAndClearCancelWithoutReplay() {
        Output output=new Output();RiskHaptics player=new RiskHaptics(output);
        player.alert(Detection.Risk.NEAR);player.update(Detection.Risk.CAPTURED);
        assertEquals(1,output.cancels);player.clear();assertEquals(2,output.cancels);
        for(int i=0;i<100;i++)player.update(Detection.Risk.NEAR);
        assertEquals(1,output.cues.size());
        player.alert(Detection.Risk.CAPTURED);assertEquals(1,output.cues.size());
    }
    @Test public void freshFeedbackExpiryCancelsRedWhileBasicYellowRemains() {
        DetectionFeedback feedback=new DetectionFeedback();
        feedback.basic(Collections.singletonList(detection(Detection.Risk.CAUTION)),100,100);
        feedback.basic(Collections.singletonList(detection(Detection.Risk.CAUTION)),600,600);
        for(long at:new long[]{300,600})feedback.depth(new DepthSnapshot(at,Collections.singletonList(detection(Detection.Risk.NEAR)),
            "构造深度",DepthSnapshot.Stage.MEASURING,true,true),0,at);
        Output output=new Output();RiskHaptics player=new RiskHaptics(output);
        player.alert(RiskHaptics.strongest(feedback.visible(600).detections));
        player.update(RiskHaptics.strongest(feedback.visible(1100).detections));assertEquals(0,output.cancels);
        player.update(RiskHaptics.strongest(feedback.visible(1101).detections));assertEquals(1,output.cancels);
        assertEquals(Detection.Risk.CAUTION,RiskHaptics.strongest(feedback.visible(1101).detections));
        player.update(RiskHaptics.strongest(feedback.visible(1601).detections));
        assertEquals(Collections.singletonList(RiskHaptics.Cue.DOUBLE),output.cues);
    }
    @Test public void firstUnconfirmedNearCannotRequestADouble() {
        DetectionFeedback feedback=new DetectionFeedback();
        feedback.depth(new DepthSnapshot(100,Collections.singletonList(detection(Detection.Risk.NEAR)),"构造深度",
            DepthSnapshot.Stage.MEASURING,true,true),0,100);
        Output output=new Output();new RiskHaptics(output).alert(RiskHaptics.strongest(feedback.visible(100).detections));
        assertTrue(output.cues.isEmpty());
    }
    @Test public void legacyPatternsAreFiniteDistinctAndCannotBeMutatedThroughACaller() {
        assertArrayEquals(new long[]{0,150},RiskHaptics.Cue.SINGLE.fallbackTimings());
        assertArrayEquals(new long[]{0,100,100,150},RiskHaptics.Cue.DOUBLE.fallbackTimings());
        long[] changed=RiskHaptics.Cue.DOUBLE.fallbackTimings();changed[1]=99999;
        assertEquals(100,RiskHaptics.Cue.DOUBLE.fallbackTimings()[1]);
        assertEquals(0,RiskHaptics.Cue.NONE.fallbackTimings().length);
    }
}
