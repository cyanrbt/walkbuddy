package com.cyanrbt.walkbuddy;

import org.junit.Test;

import static org.junit.Assert.*;

public class DepthGuideTest {
    @Test public void offScreenGuidesTowardEnableWhenSupported() {
        DepthGuide.Progress p = DepthGuide.resolve(false, true, false, null, false, false, false);
        assertEquals(DepthGuide.Step.SERVICE, p.active);
        assertTrue(p.serviceDone);
        assertFalse(p.groundDone);
        assertTrue(p.hint.contains("开启空间检测"));
        assertEquals("开启空间检测", p.buttonLabel);
    }

    @Test public void offScreenExplainsUnsupportedDevice() {
        DepthGuide.Progress p = DepthGuide.resolve(false, false, false, null, false, false, false);
        assertEquals(DepthGuide.Step.SERVICE, p.active);
        assertFalse(p.serviceDone);
        assertTrue(p.hint.contains("不支持"));
        assertEquals("增强不可用 · 说明", p.buttonLabel);
    }

    @Test public void availabilityCheckShowsBusyLabel() {
        DepthGuide.Progress p = DepthGuide.resolve(false, null, true, null, false, false, false);
        assertEquals("检测增强能力…", p.buttonLabel);
        assertTrue(p.hint.contains("检查"));
    }

    @Test public void groundSearchStaysOnGroundStep() {
        DepthGuide.Progress p = DepthGuide.resolve(true, true, false,
            DepthSnapshot.Stage.GROUND_SEARCH, false, false, false);
        assertEquals(DepthGuide.Step.GROUND, p.active);
        assertTrue(p.serviceDone);
        assertFalse(p.groundDone);
        assertTrue(p.hint.contains("地面"));
    }

    @Test public void orientAsksForForwardView() {
        DepthGuide.Progress p = DepthGuide.resolve(true, true, false,
            DepthSnapshot.Stage.ORIENT, false, false, false);
        assertEquals(DepthGuide.Step.GROUND, p.active);
        assertTrue(p.hint.contains("前方"));
    }

    @Test public void depthWaitUnlocksAfterGround() {
        DepthGuide.Progress p = DepthGuide.resolve(true, true, false,
            DepthSnapshot.Stage.DEPTH_WAIT, true, false, false);
        assertEquals(DepthGuide.Step.DEPTH, p.active);
        assertTrue(p.groundDone);
        assertFalse(p.depthDone);
        assertTrue(p.hint.contains("深度"));
    }

    @Test public void measuringWithoutRecognitionStaysOnAlertStep() {
        DepthGuide.Progress p = DepthGuide.resolve(true, true, false,
            DepthSnapshot.Stage.MEASURING, true, true, false);
        assertEquals(DepthGuide.Step.ALERT, p.active);
        assertTrue(p.depthDone);
        assertFalse(p.alertDone);
        assertTrue(p.hint.contains("检测"));
    }

    @Test public void measuringWithRecognitionCompletesGuide() {
        DepthGuide.Progress p = DepthGuide.resolve(true, true, false,
            DepthSnapshot.Stage.MEASURING, true, true, true);
        assertEquals(DepthGuide.Step.ALERT, p.active);
        assertTrue(p.alertDone);
        assertTrue(p.hint.contains("就绪") || p.hint.contains("分级"));
    }

    @Test public void sparseDepthKeepsDepthStepActive() {
        DepthGuide.Progress p = DepthGuide.resolve(true, true, false,
            DepthSnapshot.Stage.DEPTH_SPARSE, true, false, false);
        assertEquals(DepthGuide.Step.DEPTH, p.active);
        assertFalse(p.depthDone);
        assertTrue(p.hint.contains("覆盖") || p.hint.contains("对准"));
    }

    @Test public void staleDepthDoesNotClaimMeasuring() {
        DepthGuide.Progress p = DepthGuide.resolve(true, true, false,
            DepthSnapshot.Stage.DEPTH_STALE, true, false, false);
        assertEquals(DepthGuide.Step.DEPTH, p.active);
        assertFalse(p.depthDone);
        assertTrue(p.hint.contains("深度"));
    }
}
