package com.cyanrbt.walkbuddy;

import com.google.ar.core.Pose;
import java.util.*;

/** Shared host/ART comparison against the allocating projection used in v0.4.31. Test APK only. */
final class DepthProjectionCases {
    static String parity() {
        int width=DepthObstacles.WIDTH,height=DepthObstacles.HEIGHT,n=width*height,cases=0;
        DepthProjection scratch=new DepthProjection(width,height);
        DepthObstacles.Grid previous=null;long previousHash=0;
        for(int yaw:new int[]{-150,-90,-30,0,60,135})for(int pitch:new int[]{-20,25})
            for(int intrinsics=0;intrinsics<2;intrinsics++)for(boolean ground:new boolean[]{false,true})for(int frame=0;frame<2;frame++) {
                float sy=(float)Math.sin(Math.toRadians(yaw)/2),cy=(float)Math.cos(Math.toRadians(yaw)/2);
                float sx=(float)Math.sin(Math.toRadians(pitch)/2),cx=(float)Math.cos(Math.toRadians(pitch)/2);
                Pose pose=Pose.makeTranslation(frame==0?.35f:-3.1f,intrinsics==0?1.2f:1.6f,frame==0?-.8f:2.3f)
                    .compose(Pose.makeRotation(0,sy,0,cy)).compose(Pose.makeRotation(sx,0,0,cx));
                float[] forward=pose.getZAxis();float fx=-forward[0],fz=-forward[2],length=(float)Math.hypot(fx,fz);fx/=length;fz/=length;
                scratch.focal[0]=intrinsics==0?510.25f:891.75f;scratch.focal[1]=intrinsics==0?507.5f:883.125f;
                scratch.center[0]=intrinsics==0?319.5f:639.75f;scratch.center[1]=intrinsics==0?239.25f:359.5f;
                DepthObstacles.Grid expected=new DepthObstacles.Grid(width,height,ground),actual=new DepthObstacles.Grid(width,height,ground);
                for(int i=0;i<n;i++) {
                    scratch.imageCoords[2*i]=scratch.view[2*i]*(intrinsics==0?640:1280)+frame*.25f;
                    scratch.imageCoords[2*i+1]=scratch.view[2*i+1]*(intrinsics==0?480:720)-frame*.5f;
                    if((i+frame)%11==0)continue; // A skipped observation must not reuse an old point.
                    int band=(i/width)%3;float depth=band==0?.7f:band==1?1.4f:3.1f,floor=ground?.05f:Float.NaN;
                    baseline(pose,scratch,i,depth,fx,fz,floor,expected);
                    scratch.project(pose,i,depth,fx,fz,floor,actual);
                    expected.confidence[i]=actual.confidence[i]=.95f;
                }
                grids(expected,actual);
                detections(DepthObstacles.detect(expected),DepthObstacles.detect(actual));
                detections(DepthDrops.detect(expected),DepthDrops.detect(actual));
                if(previous!=null && fingerprint(previous)!=previousHash)throw new AssertionError("Projection mutated a previous grid");
                previous=actual;previousHash=fingerprint(actual);cases++;
            }
        return "DEPTH_PROJECTION_PARITY_PASS cases="+cases+" grid_points="+(cases*n)
            +"; raw float bits, obstacle/drop outputs, moving pose/intrinsics, sparse updates; constructed observations, not AR camera accuracy\n";
    }
    static void sampleCenters() {
        int width=DepthObstacles.WIDTH,height=DepthObstacles.HEIGHT;DepthProjection p=new DepthProjection(width,height);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int i=(y*width+x)*2;same((x+.5f)/width,p.view[i]);same((y+.5f)/height,p.view[i+1]);
        }
    }
    static void incompleteTransformCannotReuseOldCoordinates() {
        DepthProjection p=new DepthProjection(1,1);p.textureCoords[0]=.5f;p.textureCoords[1]=.5f;p.imageCoords[0]=320;p.imageCoords[1]=240;
        if(!p.mapped(0))throw new AssertionError("Fresh valid mapping was rejected");
        p.reset();
        if(p.mapped(0) || !Float.isNaN(p.focal[0]) || !Float.isNaN(p.center[1]))throw new AssertionError("Old transform/intrinsics retained after reset");
        p.textureCoords[0]=.5f;p.textureCoords[1]=.5f;
        if(p.mapped(0))throw new AssertionError("Missing image transform accepted");
        p.imageCoords[0]=320;if(p.mapped(0))throw new AssertionError("Partial image transform accepted");
        p.imageCoords[1]=240;
        for(float invalid:new float[]{-1f,1f,Float.NaN,Float.POSITIVE_INFINITY}) {
            p.textureCoords[0]=invalid;if(p.mapped(0))throw new AssertionError("Invalid texture coordinate accepted");
        }
        p.textureCoords[0]=0;if(!p.mapped(0))throw new AssertionError("Valid texture edge rejected");
        p.imageCoords[1]=Float.POSITIVE_INFINITY;if(p.mapped(0))throw new AssertionError("Invalid image coordinate accepted");
        same(.5f,p.view[0]);same(.5f,p.view[1]);
    }
    static void independentWorkspaces() {
        DepthProjection a=new DepthProjection(1,1),b=new DepthProjection(1,1);
        a.focal[0]=a.focal[1]=1;b.focal[0]=b.focal[1]=2;
        a.imageCoords[0]=2;a.imageCoords[1]=3;b.imageCoords[0]=-4;b.imageCoords[1]=1;
        DepthObstacles.Grid ga=new DepthObstacles.Grid(1,1,true),gb=new DepthObstacles.Grid(1,1,true);
        a.project(Pose.IDENTITY,0,1,0,-1,0,ga);long before=fingerprint(ga);
        b.project(Pose.makeTranslation(1,2,3),0,2,1,0,1,gb);
        a.project(Pose.IDENTITY,0,1,0,-1,0,new DepthObstacles.Grid(1,1,true));
        if(fingerprint(ga)!=before || a.imageCoords[0]!=2 || a.focal[0]!=1)throw new AssertionError("Workspaces or published grids share scratch");
        same(2,a.imageCoords[0]);same(-4,b.imageCoords[0]);
    }
    static void boundaryMath() {
        DepthProjection p=new DepthProjection(1,1);p.focal[0]=510;p.focal[1]=507;p.center[0]=320;p.center[1]=240;
        p.imageCoords[0]=Float.POSITIVE_INFINITY;p.imageCoords[1]=240;
        for(float z:new float[]{.2f,5f,0,Float.NaN,Float.POSITIVE_INFINITY,-0f})for(float floor:new float[]{0,Float.NaN}) {
            DepthObstacles.Grid expected=new DepthObstacles.Grid(1,1,true),actual=new DepthObstacles.Grid(1,1,true);
            baseline(Pose.IDENTITY,p,0,z,0,-1,floor,expected);p.project(Pose.IDENTITY,0,z,0,-1,floor,actual);grids(expected,actual);
        }
    }
    static void obstacleAndDropResults() {
        DepthProjection p=new DepthProjection(10,10);p.focal[0]=p.focal[1]=500;p.center[0]=320;p.center[1]=240;
        Pose pose=Pose.makeTranslation(0,1.2f,0);
        for(boolean drop:new boolean[]{false,true}) {
            DepthObstacles.Grid expected=new DepthObstacles.Grid(10,10,true),actual=new DepthObstacles.Grid(10,10,true);
            for(int y=2;y<8;y++)for(int x=3;x<7;x++) {
                int i=y*10+x;float z=drop?(y<=4?.7f:1.5f):.7f,height=drop?(y<=4?.02f:-.35f):.3f;
                p.imageCoords[2*i]=.05f*p.focal[0]/z+p.center[0];
                p.imageCoords[2*i+1]=(pose.ty()-height)*p.focal[1]/z+p.center[1];
                baseline(pose,p,i,z,0,-1,0,expected);p.project(pose,i,z,0,-1,0,actual);expected.confidence[i]=actual.confidence[i]=.95f;
            }
            grids(expected,actual);List<Detection> e=drop?DepthDrops.detect(expected):DepthObstacles.detect(expected);
            List<Detection> a=drop?DepthDrops.detect(actual):DepthObstacles.detect(actual);detections(e,a);
            if(a.size()!=1 || a.get(0).risk!=Detection.Risk.NEAR)throw new AssertionError("Constructed near obstacle/drop was lost");
        }
    }
    static String all() {
        sampleCenters();incompleteTransformCannotReuseOldCoordinates();independentWorkspaces();boundaryMath();obstacleAndDropResults();
        return parity()+"DEPTH_PROJECTION_STATE_PASS cases=5; sample centers, incomplete transform reset, independent workspaces/grids, nonfinite boundary math, near obstacle/drop\n";
    }
    /** Frozen arithmetic from v0.4.31 ArDepthCamera; deliberately uses the allocating Pose overload. */
    private static void baseline(Pose pose,DepthProjection data,int i,float z,float fx,float fz,float floorY,DepthObstacles.Grid grid) {
        float[] point=pose.transformPoint(new float[]{(data.imageCoords[2*i]-data.center[0])*z/data.focal[0],
            -(data.imageCoords[2*i+1]-data.center[1])*z/data.focal[1],-z});
        float dx=point[0]-pose.tx(),dz=point[2]-pose.tz();grid.forward[i]=dx*fx+dz*fz;grid.lateral[i]=-dx*fz+dz*fx;
        grid.aboveGround[i]=point[1]-floorY;
    }
    private static void same(float a,float b){if(Float.floatToRawIntBits(a)!=Float.floatToRawIntBits(b))throw new AssertionError("Projection float bits differ: "+a+" / "+b);}
    private static void grids(DepthObstacles.Grid a,DepthObstacles.Grid b) {
        float[][] left={a.forward,a.lateral,a.aboveGround,a.confidence},right={b.forward,b.lateral,b.aboveGround,b.confidence};
        for(int k=0;k<left.length;k++)for(int i=0;i<left[k].length;i++)same(left[k][i],right[k][i]);
    }
    private static long fingerprint(DepthObstacles.Grid grid) {
        long value=1;for(float[] values:new float[][]{grid.forward,grid.lateral,grid.aboveGround,grid.confidence})
            for(float v:values)value=31*value+Float.floatToRawIntBits(v);return value;
    }
    private static void detections(List<Detection> a,List<Detection> b) {
        if(a.size()!=b.size())throw new AssertionError("Projection changed detection count");
        for(int i=0;i<a.size();i++) {
            Detection x=a.get(i),y=b.get(i);
            if(!x.label.equals(y.label) || x.risk!=y.risk)throw new AssertionError("Projection changed detection meaning");
            same(x.score,y.score);same(x.left,y.left);same(x.top,y.top);same(x.right,y.right);same(x.bottom,y.bottom);same(x.meters,y.meters);
            if((x.mask==null)!=(y.mask==null) || x.mask!=null && (x.mask.width!=y.mask.width || x.mask.height!=y.mask.height || !Arrays.equals(x.mask.pixels,y.mask.pixels)))
                throw new AssertionError("Projection changed mask");
        }
    }
}
