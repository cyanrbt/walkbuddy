package com.cyanrbt.walkbuddy;

import java.util.*;

/** Category-independent positive obstacles. No inference that missing depth means free space. */
final class DepthObstacles {
    static final int WIDTH=DepthThresholds.GRID_WIDTH,HEIGHT=DepthThresholds.GRID_HEIGHT;
    static final class Grid {
        final int width,height;
        final float[] forward,lateral,aboveGround,confidence;
        final boolean groundKnown;
        Grid(int width,int height,boolean groundKnown) {
            this.width=width;this.height=height;this.groundKnown=groundKnown;
            int n=width*height;forward=new float[n];lateral=new float[n];aboveGround=new float[n];confidence=new float[n];
        }
    }
    static List<Detection> detect(Grid g) {
        int n=g.width*g.height;boolean[] candidate=new boolean[n],seen=new boolean[n];
        for(int i=0;i<n;i++)candidate[i]=Float.isFinite(g.forward[i]) && Float.isFinite(g.lateral[i]) && g.confidence[i]>=DepthThresholds.CONFIDENCE_MIN
            && g.forward[i]>=DepthThresholds.FORWARD_MIN && g.forward[i]<=DepthThresholds.FORWARD_MAX
            && (!g.groundKnown || (Float.isFinite(g.aboveGround[i]) && g.aboveGround[i]>=DepthThresholds.ABOVE_GROUND_MIN && g.aboveGround[i]<=DepthThresholds.ABOVE_GROUND_MAX));
        List<Detection> result=new ArrayList<>();int[] queue=new int[n];
        for(int seed=0;seed<n;seed++) {
            if(!candidate[seed] || seen[seed])continue;
            int head=0,tail=0;queue[tail++]=seed;seen[seed]=true;
            while(head<tail) {
                int p=queue[head++],x=p%g.width,y=p/g.width;
                int[] adjacent={x>0?p-1:-1,x+1<g.width?p+1:-1,y>0?p-g.width:-1,y+1<g.height?p+g.width:-1};
                for(int next:adjacent)if(next>=0 && candidate[next] && !seen[next] && Math.abs(g.forward[next]-g.forward[p])<DepthThresholds.NEIGHBOR_FORWARD_JUMP){seen[next]=true;queue[tail++]=next;}
            }
            if(tail<DepthThresholds.MIN_CLUSTER_POINTS)continue;
            boolean[] pixels=new boolean[n];int minX=g.width,minY=g.height,maxX=0,maxY=0,pathCount=0;
            float[] pathDepth=new float[tail];
            for(int k=0;k<tail;k++) {
                int p=queue[k],x=p%g.width,y=p/g.width;pixels[p]=true;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                if(g.groundKnown && Math.abs(g.lateral[p])<=DepthThresholds.PATH_HALF_WIDTH)pathDepth[pathCount++]=g.forward[p];
            }
            Detection.Risk risk=Detection.Risk.CAPTURED;float distance=Float.NaN;
            if(pathCount>=DepthThresholds.MIN_PATH_POINTS) {
                Arrays.sort(pathDepth,0,pathCount);distance=pathDepth[pathCount/4];
                if(distance<=DepthThresholds.NEAR_METERS)risk=Detection.Risk.NEAR;
                else if(distance<=DepthThresholds.CAUTION_METERS)risk=Detection.Risk.CAUTION;
            }
            result.add(new Detection("空间障碍",1,minX/(float)g.width,minY/(float)g.height,(maxX+1f)/g.width,(maxY+1f)/g.height,
                risk,distance,new ObstacleMask(g.width,g.height,pixels)));
        }
        result.sort((a,b)->Integer.compare(b.risk.ordinal(),a.risk.ordinal()));
        return result.size()>DepthThresholds.MAX_DETECTIONS?new ArrayList<>(result.subList(0,DepthThresholds.MAX_DETECTIONS)):result;
    }
}
