package com.cyanrbt.walkbuddy;

/** Rear camera axis is approximately opposite display normal (device Z). No heading inference. */
final class PosturePolicy {
    private long tiltedSince=-1,clearSince=-1;
    private boolean warning;
    boolean update(float x,float y,float z,long now) {
        float magnitude=(float)Math.sqrt(x*x+y*y+z*z);
        if(!Float.isFinite(magnitude) || magnitude<7 || magnitude>12){reset();return false;}
        float tilt=Math.abs(z)/magnitude;
        if(tilt>0.75f) {
            clearSince=-1;
            if(tiltedSince<0)tiltedSince=now;
            if(now-tiltedSince>=2000)warning=true;
        }else if(tilt<0.60f) {
            tiltedSince=-1;
            if(clearSince<0)clearSince=now;
            if(now-clearSince>=1000)warning=false;
        }else {tiltedSince=-1;clearSince=-1;}
        return warning;
    }
    void reset(){tiltedSince=clearSince=-1;warning=false;}
}
