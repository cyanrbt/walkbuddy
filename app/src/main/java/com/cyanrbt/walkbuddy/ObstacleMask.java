package com.cyanrbt.walkbuddy;

/** Coarse measured occupancy, not a semantic object silhouette. */
final class ObstacleMask {
    final int width,height;
    final boolean[] pixels;
    ObstacleMask(int width,int height,boolean[] pixels){this.width=width;this.height=height;this.pixels=pixels.clone();}
    boolean at(int x,int y){return x>=0 && y>=0 && x<width && y<height && pixels[y*width+x];}
    ObstacleMask rotate(int degrees) {
        int q=Math.floorMod(degrees/90,4),w=q%2==0?width:height,h=q%2==0?height:width;
        boolean[] out=new boolean[w*h];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int nx=x,ny=y;
            if(q==1){nx=height-1-y;ny=x;}else if(q==2){nx=width-1-x;ny=height-1-y;}else if(q==3){nx=y;ny=width-1-x;}
            out[ny*w+nx]=pixels[y*width+x];
        }
        return new ObstacleMask(w,h,out);
    }
}
