package com.cyanrbt.walkbuddy;

import java.lang.management.ManagementFactory;
import java.util.Collections;

/** Desktop JDK 17 allocation probe, not an Android FPS or battery benchmark. */
public final class FeedbackBenchmark {
    private static volatile AlertPolicy.Decision sink;

    public static void main(String[] args) {
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!bean.isThreadAllocatedMemorySupported())throw new IllegalStateException("Thread allocation counter unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        measure(bean,"empty",new DetectionFeedback());
        DetectionFeedback feedback=new DetectionFeedback();
        Detection person=new Detection("人",.8f,.3f,.1f,.7f,.9f,Detection.Risk.CAUTION,Float.NaN,null);
        feedback.basic(Collections.singletonList(person),100,100);
        feedback.basic(Collections.singletonList(person),600,600);
        for(long time:new long[]{300,600})feedback.depth(new DepthSnapshot(time,
            Collections.singletonList(new Detection("空间障碍",1,.3f,.1f,.7f,.9f,Detection.Risk.NEAR,.7f,null)),
            "深度有效",DepthSnapshot.Stage.MEASURING,true,true),0,time);
        measure(bean,"basic+depth",feedback);
    }

    private static void measure(com.sun.management.ThreadMXBean bean,String scenario,DetectionFeedback feedback) {
        for(int i=0;i<200_000;i++)sink=feedback.visible(1000);
        long thread=Thread.currentThread().getId();
        int checks=1_000_000;
        long before=bean.getThreadAllocatedBytes(thread);
        for(int i=0;i<checks;i++)sink=feedback.visible(1000);
        long allocated=bean.getThreadAllocatedBytes(thread)-before;
        System.out.println(scenario+": checks="+checks+", visible="+sink.detections.size()
            +", allocatedBytes="+allocated+", bytesPerCheck="+(allocated/(double)checks));
    }
}
