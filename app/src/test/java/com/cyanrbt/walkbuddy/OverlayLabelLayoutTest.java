package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class OverlayLabelLayoutTest {
    private static OverlayLabelLayout.Label label(String name,Detection.Risk risk,float meters,float score,
            float l,float t,float r,float b,float w,float h) {
        return new OverlayLabelLayout.Label(new Detection(name,score,0,0,1,1,risk,meters,null),l,t,r,b,w,h);
    }
    @Test public void solitaryLabelKeepsItsOriginalAboveLeftAnchor() {
        var item=label("目标",Detection.Risk.CAUTION,Float.NaN,.8f,70,83,200,160,100,30);
        var p=OverlayLabelLayout.place(new OverlayLabelLayout.Label[]{item},10,20,320,240,1)[0];
        assertEquals(70,p.left,0);assertEquals(50,p.top,0);assertFalse(p.bottomAnchor);
    }
    @Test public void crowdedSmallWindowReservesItsOnlySpaceForNearRisk() {
        var gray=label("灰框",Detection.Risk.CAPTURED,Float.NaN,1,10,20,86,60,84,37);
        var yellow=label("关注",Detection.Risk.CAUTION,Float.NaN,.9f,10,20,86,60,84,37);
        var red=label("障碍",Detection.Risk.NEAR,.7f,.6f,10,20,86,60,84,37);
        var p=OverlayLabelLayout.place(new OverlayLabelLayout.Label[]{gray,yellow,red},0,0,96,72,1);
        assertNull(p[0]);assertNull(p[1]);assertNotNull(p[2]);
    }
    @Test public void measuredNearerTargetWinsWithinTheSameRisk() {
        var far=label("远处",Detection.Risk.NEAR,.8f,1,10,20,86,60,84,37);
        var near=label("近处",Detection.Risk.NEAR,.6f,.6f,10,20,86,60,84,37);
        var p=OverlayLabelLayout.place(new OverlayLabelLayout.Label[]{far,near},0,0,96,72,1);
        assertNull(p[0]);assertNotNull(p[1]);
    }
    @Test public void nearbyAlternativeRetainsASecondLabel() {
        var a=label("甲",Detection.Risk.CAUTION,Float.NaN,.8f,50,100,270,210,100,24);
        var b=label("乙",Detection.Risk.CAUTION,Float.NaN,.7f,50,100,270,210,100,24);
        var p=OverlayLabelLayout.place(new OverlayLabelLayout.Label[]{a,b},0,0,320,240,1);
        assertNotNull(p[0]);assertNotNull(p[1]);assertEquals(p[0].top,p[1].top,0);
        assertTrue(Math.abs(p[0].left-p[1].left)>=102);
    }
    @Test public void randomCrowdsKeepGapsBoundsAssociationAndStableInputOrder() {
        Random random=new Random(394867362L);
        for(int run=0;run<256;run++) {
            float width=new int[]{96,160,320,640}[run%4],height=width*.75f;
            List<OverlayLabelLayout.Label> data=new ArrayList<>();
            for(int i=0;i<37;i++) {
                float l=10+random.nextFloat()*(width-20),t=20+random.nextFloat()*(height-20);
                float r=Math.min(10+width,l+10+random.nextFloat()*width/2),b=Math.min(20+height,t+15+random.nextFloat()*height/2);
                data.add(label("目标"+i,Detection.Risk.values()[i%3],i%2==0?.5f+i*.1f:Float.NaN,.4f+i*.01f,
                    l,t,r,b,Math.min(width-4,40+random.nextFloat()*100),22+(run%3)*8));
            }
            var input=data.toArray(new OverlayLabelLayout.Label[0]);
            var placed=OverlayLabelLayout.place(input,10,20,width,height,1);
            Map<OverlayLabelLayout.Label,OverlayLabelLayout.Position> original=new IdentityHashMap<>();
            for(int i=0;i<input.length;i++) {
                var p=placed[i];var size=input[i];original.put(size,p);if(p==null)continue;
                assertTrue(p.left>=12 && p.top>=22 && p.left+size.width<=10+width-2+.001 && p.top+size.height<=20+height-2+.001);
                float anchor=p.bottomAnchor?size.bottom:size.top;
                assertTrue(Math.max(0,Math.max(p.top-anchor,anchor-p.top-size.height))<=5.001f);
                for(int j=0;j<i;j++)if(placed[j]!=null) {
                    var other=placed[j];var extent=input[j];
                    assertTrue(p.left+size.width+2<=other.left+.001 || other.left+extent.width+2<=p.left+.001
                        || p.top+size.height+2<=other.top+.001 || other.top+extent.height+2<=p.top+.001);
                }
            }
            Collections.shuffle(data,random);var shuffled=data.toArray(new OverlayLabelLayout.Label[0]);
            var again=OverlayLabelLayout.place(shuffled,10,20,width,height,1);
            for(int i=0;i<shuffled.length;i++) {
                var old=original.get(shuffled[i]);var next=again[i];
                if(old==null){assertNull(next);continue;}
                assertNotNull(next);assertEquals(old.left,next.left,0);assertEquals(old.top,next.top,0);assertEquals(old.bottomAnchor,next.bottomAnchor);
            }
        }
    }
    @Test public void missingOrOversizedLabelsAreOmittedWithoutReducingTheirSize() {
        var tall=label("大字",Detection.Risk.NEAR,.7f,1,0,0,96,72,84,80);
        var wide=label("长字",Detection.Risk.CAUTION,Float.NaN,1,0,0,96,72,100,22);
        var data=new OverlayLabelLayout.Label[]{tall,wide,null};
        assertArrayEquals(new OverlayLabelLayout.Position[3],OverlayLabelLayout.place(data,0,0,96,72,1));
        assertArrayEquals(new OverlayLabelLayout.Position[3],OverlayLabelLayout.place(data,0,0,1,1,1));
        assertEquals(80,tall.height,0);assertEquals(100,wide.width,0);
    }
}
