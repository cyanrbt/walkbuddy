package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;

public class PosturePolicyTest {
    @Test public void chestHeightUprightDoesNotWarn() {
        PosturePolicy p=new PosturePolicy();
        assertFalse(p.update(0,9.8f,0,0));assertFalse(p.update(0,9.8f,0,3000));
        assertFalse(p.update(9.8f,0,0,6000));
    }
    @Test public void sustainedUpOrDownTiltWarnsAndRecovers() {
        for(float z:new float[]{9.8f,-9.8f}) {
            PosturePolicy p=new PosturePolicy();
            assertFalse(p.update(0,0,z,0));assertFalse(p.update(0,0,z,1999));
            assertTrue(p.update(0,0,z,2000));
            assertTrue(p.update(0,9.8f,0,2500));assertFalse(p.update(0,9.8f,0,3500));
        }
    }
    @Test public void briefTiltAndBoundaryJitterDoNotWarn() {
        PosturePolicy p=new PosturePolicy();
        p.update(0,0,9.8f,0);assertFalse(p.update(0,9.8f,0,1500));
        assertFalse(p.update(0,0,9.8f,2000));assertFalse(p.update(0,7,7,3000));
        assertFalse(p.update(0,0,9.8f,4000));assertFalse(p.update(0,0,9.8f,5500));
    }
    @Test public void resetAndInvalidInputClearWarning() {
        PosturePolicy p=new PosturePolicy();p.update(0,0,9.8f,0);assertTrue(p.update(0,0,9.8f,2000));
        assertFalse(p.update(0,0,Float.NaN,2100));
        assertFalse(p.update(0,0,30,2200));
        p.reset();assertFalse(p.update(0,0,9.8f,3000));
    }
}
