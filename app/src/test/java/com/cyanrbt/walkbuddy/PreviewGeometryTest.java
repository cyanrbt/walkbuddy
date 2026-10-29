package com.cyanrbt.walkbuddy;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewGeometryTest {
 @Test public void allSensorDisplaySizesFitWithoutStretch(){
  for(int sensor:new int[]{0,90,180,270})for(int display:new int[]{0,90,180,270})for(int[] view:new int[][]{{290,210},{600,150},{250,600},{480,640},{640,480}}){
   PreviewGeometry g=new PreviewGeometry(640,480,sensor,display,view[0],view[1]);
   assertTrue(g.left>=-0.001 && g.top>=-0.001);assertTrue(g.width<=view[0]+0.001 && g.height<=view[1]+0.001);
   assertTrue(Math.abs(g.width-view[0])<0.001 || Math.abs(g.height-view[1])<0.001);
   float expected=(sensor-display+360)%180==0?640f/480:480f/640;
   assertEquals(expected,g.width/g.height,0.0001);
   float naturalW=sensor%180==0?640:480,naturalH=sensor%180==0?480:640;
   float[] center=g.map(view[0]/2f,view[1]/2f),x=g.map(view[0]/2f+view[0]/naturalW*30,view[1]/2f),y=g.map(view[0]/2f,view[1]/2f+view[1]/naturalH*30);
   assertEquals(view[0]/2f,center[0],0.001);assertEquals(view[1]/2f,center[1],0.001);
   assertEquals(Math.hypot(x[0]-center[0],x[1]-center[1]),Math.hypot(y[0]-center[0],y[1]-center[1]),0.001);
  }
 }
 @Test public void sensorAlreadyCompensatedOnlyDisplayRotates(){
  for(int sensor:new int[]{0,90,180,270})for(int display:new int[]{0,90,180,270}){
   PreviewGeometry g=new PreviewGeometry(640,480,sensor,display,300,400);float[] top=g.map(150,0);
   switch(display){case 0:assertEquals(150,top[0],0.001);assertTrue(top[1]<200);break;case 90:assertTrue(top[0]<150);assertEquals(200,top[1],0.001);break;case 180:assertTrue(top[1]>200);break;case 270:assertTrue(top[0]>150);break;}
  }
 }
 @Test public void reopenAndResizeDeterministic(){PreviewGeometry a=new PreviewGeometry(640,480,90,270,320,180);new PreviewGeometry(640,480,90,0,210,400);assertArrayEquals(a.matrix,new PreviewGeometry(640,480,90,270,320,180).matrix,0);}
 @Test public void readbackMatchesTheModelEdgeForEveryOrientationWithoutUpscaling(){
  for(int sensor:new int[]{0,90,180,270})for(int display:new int[]{0,90,180,270}){
   PreviewGeometry g=new PreviewGeometry(640,480,sensor,display,300,400);
   assertEquals(sensor%180==0?320:240,g.sampleWidth);
   assertEquals(sensor%180==0?240:320,g.sampleHeight);
   assertEquals(640*480/4,g.sampleWidth*g.sampleHeight);
   assertEquals(g.naturalWidth/(float)g.naturalHeight,g.sampleWidth/(float)g.sampleHeight,0.0001);
   PreviewGeometry small=new PreviewGeometry(160,120,sensor,display,300,400);
   assertEquals(small.naturalWidth,small.sampleWidth);assertEquals(small.naturalHeight,small.sampleHeight);
  }
 }
 @Test(expected=IllegalArgumentException.class) public void rejectsZeroView(){new PreviewGeometry(640,480,90,0,0,400);}
}
