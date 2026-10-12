package com.cyanrbt.walkbuddy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Match individual targets; image position is a cue, never a distance estimate. */
final class AlertPolicy {
    static final class Decision {
        final String message;
        final boolean alert;
        Decision(String message,boolean alert){this.message=message;this.alert=alert;}
    }
    private static final class Track {
        final Detection detection;
        final int hits;
        long alertedAt;
        Track(Detection detection,int hits,long alertedAt){this.detection=detection;this.hits=hits;this.alertedAt=alertedAt;}
    }
    private List<Track> previous=Collections.emptyList();
    private long previousAt,lastAlert=-3000;
    Decision update(List<Detection> detections,long now) {
        boolean fresh=now>=previousAt && now-previousAt<=1200;
        boolean[] used=new boolean[previous.size()];
        List<Track> next=new ArrayList<>();Track target=null;
        for(Detection detection:detections) {
            int match=-1;float best=0.2f;
            if(fresh)for(int i=0;i<previous.size();i++) {
                Track track=previous.get(i);float overlap=detection.overlap(track.detection);
                if(!used[i] && detection.label.equals(track.detection.label) && overlap>best){best=overlap;match=i;}
            }
            Track old=match<0?null:previous.get(match);if(match>=0)used[match]=true;
            Track current=new Track(detection,old==null || !old.detection.attention() || !detection.attention()?1:old.hits+1,old==null?-8000:old.alertedAt);
            next.add(current);
            if(detection.attention() && current.hits>=2 && (target==null || current.alertedAt<target.alertedAt))target=current;
        }
        previous=next;previousAt=now;
        if(target!=null) {
            boolean alert=now-lastAlert>=3000 && now-target.alertedAt>=8000;
            if(alert){target.alertedAt=now;lastAlert=now;}
            return new Decision("请留意："+target.detection.position()+target.detection.label+"连续出现，进入画面中央关注区；距离未知",alert);
        }
        if(detections.isEmpty())return new Decision("未检测到支持目标；不代表道路安全",false);
        Detection first=detections.get(0);
        return new Decision("检测到 "+detections.size()+" 个目标（"+first.position()+first.label+"等）；等待连续确认或进入关注区",false);
    }
    void clear(){previous=Collections.emptyList();previousAt=0;}
}
