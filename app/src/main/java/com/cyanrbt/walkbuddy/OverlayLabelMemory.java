package com.cyanrbt.walkbuddy;

import java.util.Arrays;
import com.cyanrbt.walkbuddy.OverlayLabelLayout.Label;
import com.cyanrbt.walkbuddy.OverlayLabelLayout.Position;

/** Uses last-layout geometry to prefer a local slot without replaying old content. */
final class OverlayLabelMemory {
    static final long MAX_AGE_MS=1000;
    private static final float MIN_OVERLAP=.6f,MIN_MARGIN=.15f;
    private static final Label[] EMPTY_LABELS=new Label[0];
    private static final Position[] EMPTY_POSITIONS=new Position[0];
    private Label[] previousLabels=EMPTY_LABELS;
    private Position[] previousPositions=EMPTY_POSITIONS;
    private Object geometry;
    private long preparedAt=-1;

    Position[] place(Label[] labels,float left,float top,float width,float height,float dp,Object token,long now) {
        int[] preferred=null;
        if(token!=null && token==geometry && preparedAt>=0 && now>=preparedAt && now-preparedAt<=MAX_AGE_MS) {
            preferred=new int[labels.length];Arrays.fill(preferred,-1);
            for(int i=0;i<labels.length;i++) {
                int old=uniqueMatch(labels[i],previousLabels);
                if(old>=0 && previousPositions[old]!=null && uniqueMatch(previousLabels[old],labels)==i)
                    preferred[i]=previousPositions[old].slot;
            }
        }
        Position[] positions=OverlayLabelLayout.place(labels,left,top,width,height,dp,preferred);
        previousLabels=labels;previousPositions=positions;geometry=token;preparedAt=now;
        return positions;
    }
    void clear() {
        previousLabels=EMPTY_LABELS;previousPositions=EMPTY_POSITIONS;geometry=null;preparedAt=-1;
    }
    static int uniqueMatch(Label target,Label[] candidates) {
        int index=-1;float best=0,second=0;
        for(int i=0;i<candidates.length;i++) {
            float score=score(target,candidates[i]);
            if(score>best){second=best;best=score;index=i;}else if(score>second)second=score;
        }
        return best>=MIN_OVERLAP && best-second>=MIN_MARGIN?index:-1;
    }
    private static float score(Label a,Label b) {
        if(a==null || b==null)return 0;
        Detection x=a.detection,y=b.detection;
        if(!x.label.equals(y.label) || x.risk!=y.risk || Float.isFinite(x.meters)!=Float.isFinite(y.meters)
            || (x.mask==null)!=(y.mask==null))return 0;
        float intersection=Math.max(0,Math.min(a.right,b.right)-Math.max(a.left,b.left))
            *Math.max(0,Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top));
        float union=(a.right-a.left)*(a.bottom-a.top)+(b.right-b.left)*(b.bottom-b.top)-intersection;
        float overlap=union>0?intersection/union:0;
        if(!(overlap>=MIN_OVERLAP))return 0;
        // A shared immutable detection is stronger than a geometric guess; duplicates still tie.
        return x==y?2:overlap;
    }
}
