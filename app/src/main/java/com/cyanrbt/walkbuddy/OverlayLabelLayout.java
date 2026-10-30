package com.cyanrbt.walkbuddy;

import java.util.ArrayList;
import java.util.List;

/** Bounded placement near object edges; only rebuilt for new detections or geometry. */
final class OverlayLabelLayout {
    static final class Label {
        final Detection detection;
        final float left,top,right,bottom,width,height;
        Label(Detection detection,float left,float top,float right,float bottom,float width,float height) {
            this.detection=detection;this.left=left;this.top=top;this.right=right;this.bottom=bottom;
            this.width=width;this.height=height;
        }
    }
    static final class Position {
        final float left,top;
        final boolean bottomAnchor;
        final int slot;
        Position(float left,float top,int slot){this.left=left;this.top=top;this.slot=slot;this.bottomAnchor=slot/3>=2;}
    }
    static Position[] place(Label[] labels,float left,float top,float width,float height,float dp) {
        return place(labels,left,top,width,height,dp,null);
    }
    static Position[] place(Label[] labels,float left,float top,float width,float height,float dp,int[] preferred) {
        Position[] out=new Position[labels.length];List<Integer> order=new ArrayList<>();
        float margin=2*dp,gap=2*dp,offset=3*dp;
        float minX=left+margin,minY=top+margin,maxX=left+width-margin,maxY=top+height-margin;
        for(int i=0;i<labels.length;i++) {
            Label label=labels[i];
            if(label!=null && label.width>0 && label.height>0 && label.width<=maxX-minX && label.height<=maxY-minY)order.add(i);
        }
        order.sort((a,b)->compare(labels[a],labels[b]));
        for(int index:order) {
            Label label=labels[index];
            float[] xs={label.left,label.right-label.width,(label.left+label.right-label.width)/2};
            float[] ys={label.top-label.height-offset,label.top+offset,label.bottom+offset,label.bottom-label.height-offset};
            int first=preferred==null?-1:preferred[index];
            candidate:for(int attempt=-1;attempt<12;attempt++) {
                int slot=attempt<0?first:attempt;
                if(slot<0 || slot>=12 || attempt>=0 && slot==first)continue;
                float x=clamp(xs[slot%3],minX,maxX-label.width),y=clamp(ys[slot/3],minY,maxY-label.height);
                for(int other:order) {
                    Position used=out[other];if(used==null)continue;Label size=labels[other];
                    if(x<used.left+size.width+gap && x+label.width+gap>used.left
                        && y<used.top+size.height+gap && y+label.height+gap>used.top)continue candidate;
                }
                out[index]=new Position(x,y,slot);break;
            }
        }
        return out;
    }
    private static int compare(Label a,Label b) {
        int result=Integer.compare(b.detection.risk.ordinal(),a.detection.risk.ordinal());
        if(result==0)result=Float.compare(distance(a.detection),distance(b.detection));
        if(result==0)result=Float.compare(b.detection.score,a.detection.score);
        if(result==0)result=a.detection.label.compareTo(b.detection.label);
        if(result==0)result=Float.compare(a.left,b.left);
        if(result==0)result=Float.compare(a.top,b.top);
        if(result==0)result=Float.compare(a.right,b.right);
        if(result==0)result=Float.compare(a.bottom,b.bottom);
        return result;
    }
    private static float distance(Detection d){return Float.isFinite(d.meters)?d.meters:Float.POSITIVE_INFINITY;}
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(value,max));}
}
