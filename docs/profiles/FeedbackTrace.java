package com.cyanrbt.walkbuddy;
import java.util.*;
public class FeedbackTrace {
 static List<Detection> basic(){return Collections.singletonList(Detection.fromModel("人",.8f,new float[]{.1f,.3f,.9f,.7f},320,320,0,0));}
 static DepthSnapshot depth(long t,boolean near){return new DepthSnapshot(t,Collections.singletonList(new Detection("空间障碍",1,.3f,.1f,.7f,.9f,near?Detection.Risk.NEAR:Detection.Risk.CAUTION,near?.7f:1.5f,null)),"构造时序",DepthSnapshot.Stage.MEASURING,true,true);}
 static void row(String scenario,long t,String source,boolean output,DetectionFeedback f){System.out.println(scenario+"\t"+t+"\t"+source+"\t"+output+"\t"+f.visible(t).message);}
 public static void main(String[] args){
  DetectionFeedback f=new DetectionFeedback();
  row("near",100,"basic",f.basic(basic(),100,100),f);row("near",600,"basic",f.basic(basic(),600,600),f);
  row("near",700,"depth",f.depth(depth(700,true),0,700),f);row("near",900,"depth",f.depth(depth(900,true),0,900),f);
  for(long t=1000;t<=6000;t+=100)row("near",t,"depth",f.depth(depth(t,true),0,t),f);
  f=new DetectionFeedback();
  row("caution",100,"depth",f.depth(depth(100,false),0,100),f);row("caution",400,"depth",f.depth(depth(400,false),0,400),f);
  row("caution",500,"basic",f.basic(basic(),500,500),f);row("caution",700,"basic",f.basic(basic(),700,700),f);
  for(long t=800;t<=9500;t+=100)row("caution",t,"basic",f.basic(basic(),t,t),f);
 }
}