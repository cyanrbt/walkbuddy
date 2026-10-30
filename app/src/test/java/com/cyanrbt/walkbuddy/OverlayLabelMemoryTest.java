package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import com.cyanrbt.walkbuddy.OverlayLabelLayout.Label;
import com.cyanrbt.walkbuddy.OverlayLabelLayout.Position;

public class OverlayLabelMemoryTest {
    private static Label label(String name,float score,float l,float t,float r,float b) {
        return label(name,score,l,t,r,b,110,24,Detection.Risk.CAUTION,Float.NaN,null);
    }
    private static Label label(String name,float score,float l,float t,float r,float b,float width,float height,
            Detection.Risk risk,float meters,ObstacleMask mask) {
        return new Label(new Detection(name,score,l/320,t/240,r/320,b/240,risk,meters,mask),l,t,r,b,width,height);
    }
    private static Label[] frame(boolean flip,float shift) {
        return new Label[]{label("甲目标",flip?.79f:.81f,60+shift,90,260+shift,210),
            label("乙目标",flip?.81f:.79f,60+shift,90,260+shift,210)};
    }
    private static Position[] place(OverlayLabelMemory memory,Label[] data,Object token,long now) {
        return memory.place(data,0,0,320,240,1,token,now);
    }
    @Test public void scoreFlipsKeepLocalEdgesWhileCurrentBoxesMove() {
        OverlayLabelMemory memory=new OverlayLabelMemory();Object token=new Object();
        int[] slots=null;int statelessChanges=0,old=-1;
        for(int i=0;i<16;i++) {
            Label[] data=frame(i%2!=0,i);
            Position[] plain=OverlayLabelLayout.place(data,0,0,320,240,1),stable=place(memory,data,token,i*50);
            if(old>=0 && old!=plain[0].slot)statelessChanges++;old=plain[0].slot;
            if(slots==null)slots=new int[]{stable[0].slot,stable[1].slot};
            assertEquals(slots[0],stable[0].slot);assertEquals(slots[1],stable[1].slot);
            assertEquals(60+i,stable[0].left,0);assertEquals(63,stable[0].top,0);
            assertEquals(93,stable[1].top,0);
        }
        assertTrue(statelessChanges>0);
    }
    @Test public void newNearRiskTakesTheOnlySpaceFromAnOlderLowRiskLabel() {
        OverlayLabelMemory memory=new OverlayLabelMemory();Object token=new Object();
        Label low=label("候选",.8f,10,20,86,60,84,37,Detection.Risk.CAPTURED,Float.NaN,null);
        assertNotNull(memory.place(new Label[]{low},0,0,96,72,1,token,0)[0]);
        Label near=label("近障碍",.5f,10,20,86,60,84,37,Detection.Risk.NEAR,.7f,null);
        Position[] next=memory.place(new Label[]{low,near},0,0,96,72,1,token,100);
        assertNull(next[0]);assertNotNull(next[1]);
    }
    @Test public void scoreFlipsAlsoKeepHorizontalChoices() {
        OverlayLabelMemory memory=new OverlayLabelMemory();Object token=new Object();
        for(int f=0;f<8;f++) {
            Label[] data={
                label("甲目标",f%2==0?.81f:.79f,50+f,100,270+f,210,90,24,Detection.Risk.CAUTION,Float.NaN,null),
                label("乙目标",f%2==0?.79f:.81f,50+f,100,270+f,210,90,24,Detection.Risk.CAUTION,Float.NaN,null)};
            Position[] p=place(memory,data,token,f*50);
            assertEquals(0,p[0].slot);assertEquals(1,p[1].slot);
            assertEquals(50+f,p[0].left,0);assertEquals(180+f,p[1].left,0);
        }
    }
    @Test public void expiryAndReversedTimeDiscardPreferences() {
        for(long next:new long[]{1000,1001,-1}) {
            OverlayLabelMemory memory=new OverlayLabelMemory();Object token=new Object();place(memory,frame(false,0),token,0);
            Position[] p=place(memory,frame(true,0),token,next);
            assertEquals(next==1000?3:0,p[1].slot);
        }
    }
    @Test public void clearEmptyResultsAndGeometryChangesStartFresh() {
        for(int reason=0;reason<4;reason++) {
            OverlayLabelMemory memory=new OverlayLabelMemory();Object token=new Object();place(memory,frame(false,0),token,0);
            if(reason==0)memory.clear();
            if(reason==1)place(memory,new Label[0],token,50);
            if(reason==2)token=new Object();
            if(reason==3)token=null;
            assertEquals(0,place(memory,frame(true,0),token,100)[1].slot);
        }
    }
    @Test public void classRiskMeasurementAndMaskChangesGetFreshPlacement() {
        for(int change=0;change<4;change++) {
            OverlayLabelMemory memory=new OverlayLabelMemory();Object token=new Object();place(memory,frame(false,0),token,0);
            Label[] next=frame(true,0);
            next[1]=label(change==0?"新类别":"乙目标",.9f,60,90,260,210,110,24,
                change==1?Detection.Risk.NEAR:Detection.Risk.CAUTION,change==2?1.4f:Float.NaN,
                change==3?new ObstacleMask(1,1,new boolean[]{true}):null);
            assertEquals(0,place(memory,next,token,100)[1].slot);
        }
    }
    @Test public void ambiguousGeometryIsRejectedButUniqueImmutableInstancesMatch() {
        Label a=label("相同类别",.8f,60,90,260,210),b=label("相同类别",.7f,60,90,260,210);
        Label fresh=label("相同类别",.9f,60,90,260,210);
        assertEquals(-1,OverlayLabelMemory.uniqueMatch(fresh,new Label[]{a,b}));
        assertEquals(1,OverlayLabelMemory.uniqueMatch(b,new Label[]{a,b}));
        assertEquals(-1,OverlayLabelMemory.uniqueMatch(b,new Label[]{b,b}));
        assertEquals(-1,OverlayLabelMemory.uniqueMatch(null,new Label[]{a,b}));
    }
    @Test public void aSplitCannotGiveTheOldPreferenceToBothNewBoxes() {
        OverlayLabelMemory memory=new OverlayLabelMemory();Object token=new Object();place(memory,frame(false,0),token,0);
        Label[] split={label("乙目标",.9f,59,90,259,210),label("乙目标",.8f,61,90,261,210)};
        assertEquals(0,place(memory,split,token,100)[0].slot);
    }
    @Test public void largeMotionDoesNotReuseAnOldEdge() {
        OverlayLabelMemory memory=new OverlayLabelMemory();Object token=new Object();
        place(memory,frame(false,0),token,0);
        Label far=label("乙目标",.8f,280,90,310,210);
        assertEquals(0,place(memory,new Label[]{far},token,100)[0].slot);
    }
    @Test public void aPreferredEdgeUsesCurrentCoordinatesSizeAndDistance() {
        OverlayLabelMemory memory=new OverlayLabelMemory();Object token=new Object();
        Label a=label("甲目标",.8f,60,90,260,210,110,24,Detection.Risk.CAUTION,1.5f,null);
        Label b=label("乙目标",.8f,60,90,260,210,110,24,Detection.Risk.CAUTION,1.6f,null);
        assertEquals(3,place(memory,new Label[]{a,b},token,0)[1].slot);
        Label current=label("乙目标",.9f,80,100,280,220,140,30,Detection.Risk.CAUTION,1.4f,null);
        Position p=place(memory,new Label[]{current},token,100)[0];
        assertEquals(3,p.slot);assertEquals(80,p.left,0);assertEquals(103,p.top,0);
        assertEquals(1.4f,current.detection.meters,0);assertEquals(140,current.width,0);
    }
    @Test public void movingCrowdsKeepCurrentBoundsAndSpacing() {
        Random random=new Random(5054348L);
        for(int run=0;run<64;run++) {
            OverlayLabelMemory memory=new OverlayLabelMemory();Object token=new Object();
            float[] x=new float[37],y=new float[37];
            for(int i=0;i<37;i++){x[i]=random.nextFloat()*220;y[i]=random.nextFloat()*140;}
            for(int f=0;f<8;f++) {
                Label[] data=new Label[37];
                for(int i=0;i<37;i++)data[i]=label("目标"+i,.5f+random.nextFloat()*.4f,x[i]+f,y[i],x[i]+f+80,y[i]+70,
                    90,24,Detection.Risk.values()[i%3],Float.NaN,null);
                Position[] positions=place(memory,data,token,f*50);
                for(int i=0;i<37;i++)if(positions[i]!=null) {
                    Position p=positions[i];assertTrue(p.left>=2 && p.top>=2 && p.left+90<=318.001 && p.top+24<=238.001);
                    for(int j=0;j<i;j++)if(positions[j]!=null) {
                        Position q=positions[j];assertTrue(p.left+92<=q.left+.001 || q.left+92<=p.left+.001
                            || p.top+26<=q.top+.001 || q.top+26<=p.top+.001);
                    }
                }
            }
        }
    }
}
