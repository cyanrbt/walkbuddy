package com.cyanrbt.walkbuddy;

/** Coordinates in the upright, unpadded camera image, normalized to [0, 1]. */
final class Detection {
    enum Risk { CAPTURED, CAUTION, NEAR }
    final Risk risk;
    final float meters;
    final ObstacleMask mask;
    final String label;
    final float score,left,top,right,bottom;
    Detection(String label,float score,float left,float top,float right,float bottom) {
        this(label,score,left,top,right,bottom,Risk.CAPTURED,Float.NaN,null);
    }
    Detection(String label,float score,float left,float top,float right,float bottom,Risk risk,float meters,ObstacleMask mask) {
        this.risk=risk;this.meters=meters;this.mask=mask;
        this.label=label;this.score=score;this.left=left;this.top=top;this.right=right;this.bottom=bottom;
    }
    static Detection fromModel(String label,float score,float[] box,float width,float height,float padX,float padY) {
        float l=clip((box[1]*320-padX)/width),t=clip((box[0]*320-padY)/height);
        float r=clip((box[3]*320-padX)/width),b=clip((box[2]*320-padY)/height);
        return r>l && b>t ? new Detection(label,score,l,t,r,b) : null;
    }
    private static float clip(float v){return Math.max(0,Math.min(1,v));}
    Detection withRisk(Risk value){return new Detection(label,score,left,top,right,bottom,value,meters,mask);}
    boolean attention(){return risk!=Risk.CAPTURED;}
    Detection rotate(int degrees) {
        int q=Math.floorMod(degrees/90,4);float l=left,t=top,r=right,b=bottom;
        if(q==1){l=1-bottom;t=left;r=1-top;b=right;}else if(q==2){l=1-right;t=1-bottom;r=1-left;b=1-top;}
        else if(q==3){l=top;t=1-right;r=bottom;b=1-left;}
        return new Detection(label,score,l,t,r,b,risk,meters,mask==null?null:mask.rotate(degrees));
    }
    String position(){float x=(left+right)/2;return x<0.33f?"左侧":x>0.67f?"右侧":"中间";}
    float overlap(Detection other) {
        float intersection=Math.max(0,Math.min(right,other.right)-Math.max(left,other.left))
            *Math.max(0,Math.min(bottom,other.bottom)-Math.max(top,other.top));
        float union=(right-left)*(bottom-top)+(other.right-other.left)*(other.bottom-other.top)-intersection;
        return union>0?intersection/union:0;
    }
}
