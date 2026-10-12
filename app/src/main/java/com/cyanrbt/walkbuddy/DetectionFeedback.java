package com.cyanrbt.walkbuddy;

import java.util.*;

/** Independent confirmation and expiry: unavailable depth never erases fresh RGB results. */
final class DetectionFeedback {
    private final AlertPolicy basicPolicy=new AlertPolicy(),depthPolicy=new AlertPolicy();
    private AlertPolicy.Decision basic,depth;
    private long basicAt=-1,depthAt=-1,lastDepth=-1,lastAlert=-3000;
    boolean basic(List<Detection> detections,long capturedAt,long now) {
        if(now<capturedAt || now-capturedAt>1000)return false;
        basic=basicPolicy.update(detections,now);basicAt=capturedAt;
        return emit(basic,now);
    }
    boolean depth(DepthSnapshot snapshot,int rotation,long now) {
        if(snapshot==null || now<snapshot.sampledAt || now-snapshot.sampledAt>500){depth=null;depthAt=-1;depthPolicy.clear();return false;}
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
        List<Detection> out=new ArrayList<>();String message="尚未捕获目标 · 不代表前方可通行";
        if(basic!=null && now-basicAt<=1000){out.addAll(basic.detections);message=basic.message;}
        if(depth!=null && now-depthAt<=500){
            out.addAll(depth.detections);
            if(depth.detections.stream().anyMatch(Detection::attention))message=depth.message;
        }
        return new AlertPolicy.Decision(message,false,out);
    }
    void clear(){basic=null;depth=null;basicAt=depthAt=lastDepth=-1;basicPolicy.clear();depthPolicy.clear();}
}
