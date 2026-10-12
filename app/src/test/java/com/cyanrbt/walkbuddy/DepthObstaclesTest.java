package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class DepthObstaclesTest {
    private DepthObstacles.Grid patch(boolean ground,float distance,float lateral,float height,float confidence) {
        DepthObstacles.Grid g=new DepthObstacles.Grid(10,10,ground);
        for(int y=2;y<8;y++)for(int x=3;x<7;x++){int i=y*10+x;g.forward[i]=distance;g.lateral[i]=lateral;g.aboveGround[i]=height;g.confidence[i]=confidence;}
        return g;
    }
    @Test public void unknownObjectInPathIsDetectedWithoutCategory() {
        List<Detection> found=DepthObstacles.detect(patch(true,1.4f,0.1f,0.3f,0.9f));
        assertEquals(1,found.size());assertEquals("空间障碍",found.get(0).label);
        assertEquals(Detection.Risk.CAUTION,found.get(0).risk);assertNotNull(found.get(0).mask);
    }
    @Test public void onlyReliableNearDepthCanBeRed() {
        assertEquals(Detection.Risk.NEAR,DepthObstacles.detect(patch(true,0.7f,0,0.5f,0.9f)).get(0).risk);
        assertEquals(Detection.Risk.CAPTURED,DepthObstacles.detect(patch(false,0.7f,0,0.5f,0.9f)).get(0).risk);
        assertTrue(DepthObstacles.detect(patch(true,0.7f,0,0.5f,0.4f)).isEmpty());
    }
    @Test public void floorAndOverheadAreNotWalkingObstacles() {
        assertTrue(DepthObstacles.detect(patch(true,0.7f,0,0,0.9f)).isEmpty());
        assertTrue(DepthObstacles.detect(patch(true,0.7f,0,2.5f,0.9f)).isEmpty());
        assertTrue(DepthObstacles.detect(patch(true,0.7f,0,Float.NaN,0.9f)).isEmpty());
    }
    @Test public void sideObjectDoesNotBecomeNearCollision() {
        assertEquals(Detection.Risk.CAPTURED,DepthObstacles.detect(patch(true,0.5f,0.8f,0.3f,0.9f)).get(0).risk);
    }
    @Test public void missingDepthAndSpecklesAreNotAnObstacle() {
        assertTrue(DepthObstacles.detect(patch(true,0,0,0.5f,0.9f)).isEmpty());
        DepthObstacles.Grid g=new DepthObstacles.Grid(10,10,true);g.forward[5]=0.5f;g.confidence[5]=1;g.aboveGround[5]=0.4f;
        assertTrue(DepthObstacles.detect(g).isEmpty());
    }
    @Test public void redRequiresTwoIndependentObservationsAndEscalatesYellow() {
        AlertPolicy p=new AlertPolicy();List<Detection> yellow=DepthObstacles.detect(patch(true,1.5f,0,0.3f,1));
        assertFalse(p.update(yellow,100).alert);assertTrue(p.update(yellow,600).alert);
        List<Detection> red=DepthObstacles.detect(patch(true,0.7f,0,0.3f,1));
        AlertPolicy.Decision first=p.update(red,1100);assertFalse(first.alert);assertEquals(Detection.Risk.CAUTION,first.detections.get(0).risk);
        AlertPolicy.Decision second=p.update(red,1600);assertTrue(second.alert);assertEquals(Detection.Risk.NEAR,second.detections.get(0).risk);
        assertFalse(p.update(red,2100).alert);
        p.clear();assertEquals(Detection.Risk.CAPTURED,p.update(red,2200).detections.get(0).risk);
    }
    @Test public void maskAndBoundingBoxRotateTogether() {
        boolean[] pixels={true,false,false,false,false,false};ObstacleMask m=new ObstacleMask(2,3,pixels);pixels[0]=false;
        assertTrue(m.at(0,0));ObstacleMask rotated=m.rotate(-90);assertEquals(3,rotated.width);assertEquals(2,rotated.height);assertTrue(rotated.at(0,1));
        assertArrayEquals(m.pixels,rotated.rotate(90).pixels);
        Detection d=new Detection("空间障碍",1,0,0,0.5f,1f/3,Detection.Risk.NEAR,0.5f,m).rotate(-90);
        assertEquals(0,d.left,0);assertEquals(0.5f,d.top,0);assertEquals(1f/3,d.right,0.0001);assertEquals(1,d.bottom,0);
    }
}
