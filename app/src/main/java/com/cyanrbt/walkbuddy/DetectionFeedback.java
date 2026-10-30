package com.cyanrbt.walkbuddy;

import java.util.*;

/** Independent confirmation and expiry: unavailable depth never erases fresh RGB results. */
final class DetectionFeedback {
    private final AlertPolicy basicPolicy=new AlertPolicy(),depthPolicy=new AlertPolicy();
    private AlertPolicy.Decision basic,depth;
    private static final AlertPolicy.Decision EMPTY = new AlertPolicy.Decision(
        "不代表可通行 · 尚未捕获目标", false, Collections.emptyList());
    private AlertPolicy.Decision visibleBasic, visibleDepth, visible = EMPTY;
    private long basicAt=-1,depthAt=-1,lastDepth=-1,lastAlert=-3000;
    boolean basic(List<Detection> detections,long capturedAt,long now) {
        if(now<capturedAt || now-capturedAt>1000)return false;
        basic=basicPolicy.update(detections,now,lastAlert);basicAt=capturedAt;
        return emit(basic,now);
    }
    boolean depth(DepthSnapshot snapshot,int rotation,long now) {
        if(snapshot==null || !snapshot.fresh(now)){depth=null;depthAt=-1;depthPolicy.clear();return false;}
        if(lastDepth==snapshot.sampledAt)return false;
        lastDepth=snapshot.sampledAt;
        depth=depthPolicy.update(snapshot.rotated(rotation),now,lastAlert);depthAt=snapshot.sampledAt;
        return emit(depth,now);
    }
    private boolean emit(AlertPolicy.Decision decision,long now) {
        if(!decision.alert)return false;
        lastAlert=now;return true;
    }
    AlertPolicy.Decision visible(long now) {
        AlertPolicy.Decision nextBasic=basic!=null && now>=basicAt && now-basicAt<=1000?basic:null;
        AlertPolicy.Decision nextDepth=depth!=null && now>=depthAt && now-depthAt<=500?depth:null;
        if(nextBasic==visibleBasic && nextDepth==visibleDepth)return visible;
        visibleBasic=nextBasic;visibleDepth=nextDepth;
        if(nextBasic==null && nextDepth==null)return visible=EMPTY;
        List<Detection> out=new ArrayList<>();String message=EMPTY.message;
        if(nextBasic!=null){out.addAll(nextBasic.detections);message=nextBasic.message;}
        if(nextDepth!=null){
            out.addAll(nextDepth.detections);
            if(nextDepth.detections.stream().anyMatch(Detection::attention))message=nextDepth.message;
        }
        return visible=new AlertPolicy.Decision(message,false,out);
    }
    /** Last instant at which an already allowed generic cue still has a contributing result. */
    long validUntil(long now,Detection.Risk risk) {
        if(risk==Detection.Risk.CAPTURED)return -1;
        long until=-1;
        if(basic!=null && now>=basicAt && now-basicAt<=1000 && containsRisk(basic,risk))until=basicAt+1000;
        if(depth!=null && now>=depthAt && now-depthAt<=500 && containsRisk(depth,risk))until=Math.max(until,depthAt+500);
        return until;
    }
    private static boolean containsRisk(AlertPolicy.Decision decision,Detection.Risk risk) {
        for(Detection detection:decision.detections)if(detection.risk.ordinal()>=risk.ordinal())return true;
        return false;
    }
    void clear(){basic=depth=visibleBasic=visibleDepth=null;visible=EMPTY;basicAt=depthAt=lastDepth=-1;basicPolicy.clear();depthPolicy.clear();}
}
