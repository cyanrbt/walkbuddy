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
        basic=basicPolicy.update(detections,now);basicAt=capturedAt;
        return emit(basic,now);
    }
    boolean depth(DepthSnapshot snapshot,int rotation,long now) {
        if(snapshot==null || !snapshot.fresh(now)){depth=null;depthAt=-1;depthPolicy.clear();return false;}
        if(lastDepth==snapshot.sampledAt)return false;
        lastDepth=snapshot.sampledAt;
        depth=depthPolicy.update(snapshot.rotated(rotation),now);depthAt=snapshot.sampledAt;
        return emit(depth,now);
    }
    private boolean emit(AlertPolicy.Decision decision,long now) {
        boolean near=decision.detections.stream().anyMatch(d->d.risk==Detection.Risk.NEAR);
        if(!decision.alert || now-lastAlert<(near?1000:3000))return false;
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
    void clear(){basic=depth=visibleBasic=visibleDepth=null;visible=EMPTY;basicAt=depthAt=lastDepth=-1;basicPolicy.clear();depthPolicy.clear();}
}
