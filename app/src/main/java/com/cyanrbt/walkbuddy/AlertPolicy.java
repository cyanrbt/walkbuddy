package com.cyanrbt.walkbuddy;

import java.util.*;

/** Temporal confirmation belongs to measured occupancy, independent of semantic labels. */
final class AlertPolicy {
    static final class Decision {
        final String message;final boolean alert;final List<Detection> detections;
        Decision(String message,boolean alert,List<Detection> detections){this.message=message;this.alert=alert;this.detections=Collections.unmodifiableList(detections);}
    }
    private static final class Track {
        final Detection detection;final int hits,nearHits;
        long alertedAt;int alertedRisk;
        Track(Detection d,int hits,int nearHits,long at,int risk){detection=d;this.hits=hits;this.nearHits=nearHits;alertedAt=at;alertedRisk=risk;}
    }
    private List<Track> previous=Collections.emptyList();
    private long previousAt,lastAlert=-3000;
    Decision update(List<Detection> detections,long now) {
        boolean fresh=now>=previousAt && now-previousAt<=1200;
        boolean[] used=new boolean[previous.size()];List<Track> next=new ArrayList<>();List<Detection> visible=new ArrayList<>();Track target=null;
        for(Detection d:detections) {
            int match=-1;float best=0.2f;
            if(fresh)for(int i=0;i<previous.size();i++) {
                Track old=previous.get(i);float overlap=d.overlap(old.detection);
                if(!used[i] && d.label.equals(old.detection.label) && overlap>best){best=overlap;match=i;}
            }
            Track old=match<0?null:previous.get(match);if(match>=0)used[match]=true;
            int hits=old==null || !old.detection.attention() || !d.attention()?1:old.hits+1;
            int nearHits=d.risk==Detection.Risk.NEAR?(old==null?1:old.nearHits+1):0;
            Track current=new Track(d,hits,nearHits,old==null?-8000:old.alertedAt,old==null?0:old.alertedRisk);next.add(current);
            Detection.Risk confirmed=hits>=2?d.risk:Detection.Risk.CAPTURED;
            if(confirmed==Detection.Risk.NEAR && nearHits<2)confirmed=Detection.Risk.CAUTION;
            visible.add(d.withRisk(confirmed));
            if(confirmed!=Detection.Risk.CAPTURED && (target==null || confirmed.ordinal()>confirmed(target).ordinal()
                || (confirmed==confirmed(target) && current.alertedAt<target.alertedAt)))target=current;
        }
        previous=next;previousAt=now;
        if(target!=null) {
            boolean urgent=confirmed(target)==Detection.Risk.NEAR;
            boolean escalation=urgent && target.alertedRisk<2;
            boolean alert=now-lastAlert>=(escalation?1000:3000) && (escalation || now-target.alertedAt>=(urgent?4000:8000));
            if(alert){target.alertedAt=now;target.alertedRisk=confirmed(target).ordinal();lastAlert=now;}
            String distance=Float.isFinite(target.detection.meters)?String.format(Locale.CHINA,"约 %.1f 米",target.detection.meters):"未测距";
            return new Decision(distance+" · "+(urgent?"请及时注意":Float.isFinite(target.detection.meters)?"前方可能受阻":"画面关注 · "+target.detection.label)+" · "+target.detection.position(),alert,visible);
        }
        return new Decision(detections.isEmpty()?"不代表可通行 · 尚未捕获目标":"灰色不代表安全 · 已捕获 "+detections.size()+" 处目标",false,visible);
    }
    private static Detection.Risk confirmed(Track track){return track.detection.risk==Detection.Risk.NEAR && track.nearHits<2?Detection.Risk.CAUTION:track.detection.risk;}
    void clear(){previous=Collections.emptyList();previousAt=0;}
}
