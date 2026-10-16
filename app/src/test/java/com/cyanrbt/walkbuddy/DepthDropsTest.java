package com.cyanrbt.walkbuddy;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DepthDropsTest {
    /** Floor strip near the phone, drop strip farther out — a step-down edge. */
    private DepthObstacles.Grid stepDown(boolean ground) {
        DepthObstacles.Grid g = new DepthObstacles.Grid(10, 10, ground);
        for (int y = 2; y < 8; y++) {
            for (int x = 3; x < 7; x++) {
                int i = y * 10 + x;
                g.forward[i] = y <= 4 ? 1.2f : 2.4f;
                g.lateral[i] = 0.05f;
                g.aboveGround[i] = y <= 4 ? 0.02f : -0.4f;
                g.confidence[i] = 0.9f;
            }
        }
        return g;
    }

    @Test public void stepDownEdgeIsReportedAsDrop() {
        List<Detection> drops = DepthDrops.detect(stepDown(true));
        assertEquals(1, drops.size());
        assertEquals("落差", drops.get(0).label);
        assertTrue(drops.get(0).mask != null);
    }

    @Test public void dropNeedsConfirmedGround() {
        assertTrue(DepthDrops.detect(stepDown(false)).isEmpty());
    }

    @Test public void shallowDipIsNotADrop() {
        DepthObstacles.Grid g = stepDown(true);
        for (int i = 0; i < g.forward.length; i++) if (Float.isFinite(g.aboveGround[i]) && g.aboveGround[i] < 0) g.aboveGround[i] = -0.1f;
        assertTrue(DepthDrops.detect(g).isEmpty());
    }

    @Test public void missingDepthIsNeverAHole() {
        DepthObstacles.Grid g = new DepthObstacles.Grid(10, 10, true);
        for (int y = 2; y < 8; y++) for (int x = 3; x < 7; x++) {
            int i = y * 10 + x;
            g.forward[i] = 1.5f;
            g.lateral[i] = 0.05f;
            g.aboveGround[i] = 0.02f;
            g.confidence[i] = 0.9f;
        }
        // leave the far half without samples
        for (int y = 5; y < 8; y++) for (int x = 3; x < 7; x++) {
            int i = y * 10 + x;
            g.forward[i] = Float.NaN;
            g.aboveGround[i] = Float.NaN;
        }
        assertTrue(DepthDrops.detect(g).isEmpty());
    }

    @Test public void dropWithoutFloorEdgeIsIgnored() {
        DepthObstacles.Grid g = new DepthObstacles.Grid(10, 10, true);
        for (int y = 2; y < 8; y++) for (int x = 3; x < 7; x++) {
            int i = y * 10 + x;
            g.forward[i] = 2.2f;
            g.lateral[i] = 0.05f;
            g.aboveGround[i] = -0.5f;
            g.confidence[i] = 0.9f;
        }
        assertTrue(DepthDrops.detect(g).isEmpty());
    }

    @Test public void closeDropInPathCanEscalate() {
        DepthObstacles.Grid g = new DepthObstacles.Grid(10, 10, true);
        for (int y = 2; y < 8; y++) {
            for (int x = 3; x < 7; x++) {
                int i = y * 10 + x;
                g.forward[i] = y <= 4 ? 0.7f : 1.5f;
                g.lateral[i] = 0.05f;
                g.aboveGround[i] = y <= 4 ? 0.02f : -0.35f;
                g.confidence[i] = 0.95f;
            }
        }
        List<Detection> drops = DepthDrops.detect(g);
        assertEquals(1, drops.size());
        assertEquals(Detection.Risk.NEAR, drops.get(0).risk);
        assertEquals(0.7f, drops.get(0).meters, 0.01f);
    }
}
