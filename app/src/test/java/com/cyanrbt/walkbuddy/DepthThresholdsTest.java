package com.cyanrbt.walkbuddy;

import org.junit.Test;

import static org.junit.Assert.*;

/** Locks experimental defaults so a calibration change is deliberate and reviewed. */
public class DepthThresholdsTest {
    @Test public void distanceGradesAreOrderedAndWalkable() {
        assertTrue(DepthThresholds.NEAR_METERS > 0);
        assertTrue(DepthThresholds.CAUTION_METERS > DepthThresholds.NEAR_METERS);
        assertTrue(DepthThresholds.FORWARD_MIN < DepthThresholds.NEAR_METERS);
        assertTrue(DepthThresholds.NEAR_METERS <= DepthThresholds.FORWARD_MAX);
        assertTrue(DepthThresholds.CAUTION_METERS <= DepthThresholds.FORWARD_MAX);
    }

    @Test public void pathCorridorIsNarrowerThanDetectedRange() {
        assertTrue(DepthThresholds.PATH_HALF_WIDTH > 0);
        assertTrue(DepthThresholds.PATH_HALF_WIDTH < DepthThresholds.FORWARD_MAX);
    }

    @Test public void experimentalDefaultsMatchDocumentedChecklist() {
        // docs/深度阈值标定.md table is the human-facing mirror of these values.
        assertEquals(0.65f, DepthThresholds.CONFIDENCE_MIN, 0f);
        assertEquals(0.2f, DepthThresholds.FORWARD_MIN, 0f);
        assertEquals(4f, DepthThresholds.FORWARD_MAX, 0f);
        assertEquals(0.10f, DepthThresholds.ABOVE_GROUND_MIN, 0f);
        assertEquals(2.1f, DepthThresholds.ABOVE_GROUND_MAX, 0f);
        assertEquals(0.35f, DepthThresholds.NEIGHBOR_FORWARD_JUMP, 0f);
        assertEquals(8, DepthThresholds.MIN_CLUSTER_POINTS);
        assertEquals(0.4f, DepthThresholds.PATH_HALF_WIDTH, 0f);
        assertEquals(8, DepthThresholds.MIN_PATH_POINTS);
        assertEquals(0.9f, DepthThresholds.NEAR_METERS, 0f);
        assertEquals(2f, DepthThresholds.CAUTION_METERS, 0f);
        assertEquals(12, DepthThresholds.MAX_DETECTIONS);
        assertEquals(100, DepthThresholds.MIN_VALID_DEPTH_SAMPLES);
        assertEquals(0.85f, DepthThresholds.GROUND_PHONE_HEIGHT_MIN, 0f);
        assertEquals(1.8f, DepthThresholds.GROUND_PHONE_HEIGHT_MAX, 0f);
        assertEquals(0.5f, DepthThresholds.GROUND_MIN_AREA, 0f);
    }

    @Test public void occupancyGridUsesThresholdConstants() {
        assertEquals(DepthThresholds.GRID_WIDTH, DepthObstacles.WIDTH);
        assertEquals(DepthThresholds.GRID_HEIGHT, DepthObstacles.HEIGHT);
    }
}
