package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;

public class PipSourceBoundsTest {
    @Test public void portraitHintExcludesSideBarsAndIncludesTheWindowOffset() {
        PreviewGeometry g=new PreviewGeometry(640,480,90,0,360,400);
        assertArrayEquals(new int[]{50,80,350,480},g.visibleBounds(20,80,20,80,380,480));
    }
    @Test public void landscapeHintExcludesTopAndBottomBars() {
        PreviewGeometry g=new PreviewGeometry(640,480,90,90,400,360);
        assertArrayEquals(new int[]{30,70,430,370},g.visibleBounds(30,40,30,40,430,400));
    }
    @Test public void allSensorAndDisplayRotationsPreserveTheVisibleImageAspect() {
        for(int sensor:new int[]{0,90,180,270})for(int display:new int[]{0,90,180,270}) {
            PreviewGeometry g=new PreviewGeometry(640,480,sensor,display,400,400);
            int[] actual=g.visibleBounds(15,25,15,25,415,425);
            assertArrayEquals(Math.floorMod(sensor-display,180)==0?new int[]{15,75,415,375}:new int[]{65,25,365,425},actual);
        }
    }
    @Test public void partiallyOccludedCameraUsesOnlyVisiblePixels() {
        PreviewGeometry g=new PreviewGeometry(640,480,90,0,360,400);
        assertArrayEquals(new int[]{80,110,300,420},g.visibleBounds(20,80,80,110,300,420));
    }
    @Test public void offscreenAndEmptyIntersectionsDoNotInventAHint() {
        PreviewGeometry g=new PreviewGeometry(640,480,90,0,360,400);
        assertNull(g.visibleBounds(20,80,0,0,40,100));
        assertNull(g.visibleBounds(20,80,100,100,100,400));
    }
    @Test public void subpixelEdgesRoundOutwardWithoutEscapingTheVisibleView() {
        PreviewGeometry g=new PreviewGeometry(640,480,90,0,321,401);
        assertArrayEquals(new int[]{10,20,311,421},g.visibleBounds(0,20,0,20,321,421));
    }
    @Test public void aResizedDestinationIsRecomputedInsteadOfReusingTheEntryBox() {
        PreviewGeometry before=new PreviewGeometry(640,480,90,0,360,400);
        PreviewGeometry after=new PreviewGeometry(640,480,90,0,360,320);
        assertArrayEquals(new int[]{30,0,330,400},before.visibleBounds(0,0,0,0,360,400));
        assertArrayEquals(new int[]{60,0,300,320},after.visibleBounds(0,0,0,0,360,320));
    }
}
