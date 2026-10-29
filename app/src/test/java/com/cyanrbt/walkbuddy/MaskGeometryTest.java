package com.cyanrbt.walkbuddy;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class MaskGeometryTest {
    @Test public void everyThreeByThreeMaskPreservesExactFillAndBoundary() {
        for(int bits=0;bits<512;bits++) {
            boolean[] cells=new boolean[9];
            for(int i=0;i<9;i++)cells[i]=(bits&(1<<i))!=0;
            check(new ObstacleMask(3,3,cells));
        }
    }

    @Test public void wideTallAndRotatedMasksPreserveHolesAndDisconnectedRegions() {
        ObstacleMask wide=new ObstacleMask(5,3,new boolean[]{
            true,true,true,false,true,
            true,false,true,false,false,
            true,true,true,false,true});
        for(int degrees:new int[]{0,90,180,270})check(wide.rotate(degrees));
        check(new ObstacleMask(1,4,new boolean[]{true,false,true,true}));
    }

    @Test public void solidProductionGridUsesFourContinuousSidesAndOneFillPerRow() {
        int w=DepthObstacles.WIDTH,h=DepthObstacles.HEIGHT;
        boolean[] cells=new boolean[w*h];Arrays.fill(cells,true);
        MaskGeometry outline=new MaskGeometry(new ObstacleMask(w,h,cells));
        assertEquals(4,outline.edges.size());
        assertEquals(h,outline.fills.size());
        for(MaskGeometry.Run edge:outline.edges)
            assertEquals(edge.top==edge.bottom?w:h,edge.right-edge.left+edge.bottom-edge.top);
    }

    private static void check(ObstacleMask mask) {
        MaskGeometry geometry=new MaskGeometry(mask);
        int[] covered=new int[mask.width*mask.height];
        for(MaskGeometry.Run fill:geometry.fills) {
            assertTrue(fill.right>fill.left);assertEquals(fill.top+1,fill.bottom);
            for(int y=fill.top;y<fill.bottom;y++)for(int x=fill.left;x<fill.right;x++)
                covered[y*mask.width+x]++;
        }
        for(int y=0;y<mask.height;y++)for(int x=0;x<mask.width;x++)
            assertEquals(mask.at(x,y)?1:0,covered[y*mask.width+x]);

        Set<String> expected=new HashSet<>(),actual=new HashSet<>();
        // Independent oracle: expose each occupied cell's four sides, never its interior seams.
        for(int y=0;y<mask.height;y++)for(int x=0;x<mask.width;x++)if(mask.at(x,y)) {
            if(!mask.at(x,y-1))expected.add(key(x,y,x+1,y));
            if(!mask.at(x,y+1))expected.add(key(x,y+1,x+1,y+1));
            if(!mask.at(x-1,y))expected.add(key(x,y,x,y+1));
            if(!mask.at(x+1,y))expected.add(key(x+1,y,x+1,y+1));
        }
        for(MaskGeometry.Run run:geometry.edges) {
            assertTrue(run.left==run.right ^ run.top==run.bottom);
            if(run.top==run.bottom)for(int x=run.left;x<run.right;x++)
                assertTrue("duplicate edge",actual.add(key(x,run.top,x+1,run.top)));
            else for(int y=run.top;y<run.bottom;y++)
                assertTrue("duplicate edge",actual.add(key(run.left,y,run.left,y+1)));
        }
        assertEquals(expected,actual);
    }

    private static String key(int l,int t,int r,int b) {return l+","+t+","+r+","+b;}
}
