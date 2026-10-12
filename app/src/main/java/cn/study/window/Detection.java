package cn.study.window;

/** Coordinates in the upright, unpadded camera image, normalized to [0, 1]. */
final class Detection {
    final String label;
    final float score,left,top,right,bottom;
    Detection(String label,float score,float left,float top,float right,float bottom) {
        this.label=label;this.score=score;this.left=left;this.top=top;this.right=right;this.bottom=bottom;
    }
    static Detection fromModel(String label,float score,float[] box,float width,float height,float padX,float padY) {
        float l=clip((box[1]*320-padX)/width),t=clip((box[0]*320-padY)/height);
        float r=clip((box[3]*320-padX)/width),b=clip((box[2]*320-padY)/height);
        return r>l && b>t ? new Detection(label,score,l,t,r,b) : null;
    }
    private static float clip(float v){return Math.max(0,Math.min(1,v));}
    boolean attention(){return score>=0.5f && right>0.25f && left<0.75f && bottom>=0.45f;}
    String position(){float x=(left+right)/2;return x<0.33f?"左侧":x>0.67f?"右侧":"中间";}
    float overlap(Detection other) {
        float intersection=Math.max(0,Math.min(right,other.right)-Math.max(left,other.left))
            *Math.max(0,Math.min(bottom,other.bottom)-Math.max(top,other.top));
        float union=(right-left)*(bottom-top)+(other.right-other.left)*(other.bottom-other.top)-intersection;
        return union>0?intersection/union:0;
    }
}
