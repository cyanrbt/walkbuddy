package com.cyanrbt.walkbuddy;

import com.google.ar.core.Pose;
import java.util.Arrays;

/** Scratch owned by one AR camera worker. Frame outputs only receive copied scalar values. */
final class DepthProjection {
    final float[] view,textureCoords,imageCoords;
    final float[] focal=new float[2],center=new float[2];
    private final float[] cameraPoint=new float[3],worldPoint=new float[3];

    DepthProjection(int width,int height) {
        int size=width*height*2;view=new float[size];textureCoords=new float[size];imageCoords=new float[size];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int i=(y*width+x)*2;view[i]=(x+0.5f)/width;view[i+1]=(y+0.5f)/height;
        }
    }
    /** ARCore leaves output unchanged for an old Frame; never treat retained coordinates as new. */
    void reset() {
        Arrays.fill(textureCoords,Float.NaN);Arrays.fill(imageCoords,Float.NaN);
        Arrays.fill(focal,Float.NaN);Arrays.fill(center,Float.NaN);
    }
    boolean mapped(int index) {
        float u=textureCoords[2*index],v=textureCoords[2*index+1];
        return u>=0 && u<1 && v>=0 && v<1 && Float.isFinite(imageCoords[2*index]) && Float.isFinite(imageCoords[2*index+1]);
    }
    void project(Pose pose,int index,float depth,float forwardX,float forwardZ,float floorY,DepthObstacles.Grid grid) {
        cameraPoint[0]=(imageCoords[2*index]-center[0])*depth/focal[0];
        cameraPoint[1]=-(imageCoords[2*index+1]-center[1])*depth/focal[1];
        cameraPoint[2]=-depth;
        pose.transformPoint(cameraPoint,0,worldPoint,0);
        float dx=worldPoint[0]-pose.tx(),dz=worldPoint[2]-pose.tz();
        grid.forward[index]=dx*forwardX+dz*forwardZ;
        grid.lateral[index]=-dx*forwardZ+dz*forwardX;
        grid.aboveGround[index]=worldPoint[1]-floorY;
    }
}
