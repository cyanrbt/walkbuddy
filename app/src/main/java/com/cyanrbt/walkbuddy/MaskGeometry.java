package com.cyanrbt.walkbuddy;

import java.util.ArrayList;
import java.util.List;

/** Row fills and continuous straight boundary runs, in grid coordinates, including holes. */
final class MaskGeometry {
    static final class Run {
        final int left, top, right, bottom;
        Run(int left, int top, int right, int bottom) {
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
    }
    final List<Run> fills = new ArrayList<>();
    final List<Run> edges = new ArrayList<>();

    MaskGeometry(ObstacleMask mask) {
        for(int y=0;y<mask.height;y++) {
            int start=-1;
            for(int x=0;x<=mask.width;x++) {
                boolean occupied=mask.at(x,y);
                if(occupied && start<0)start=x;
                if(!occupied && start>=0){fills.add(new Run(start,y,x,y+1));start=-1;}
            }
        }
        // Joining adjacent unit edges lets a dash continue across small depth cells.
        for(int y=0;y<=mask.height;y++) {
            int start=-1;
            for(int x=0;x<=mask.width;x++) {
                boolean edge=x<mask.width && mask.at(x,y-1)!=mask.at(x,y);
                if(edge && start<0)start=x;
                if(!edge && start>=0){edges.add(new Run(start,y,x,y));start=-1;}
            }
        }
        for(int x=0;x<=mask.width;x++) {
            int start=-1;
            for(int y=0;y<=mask.height;y++) {
                boolean edge=y<mask.height && mask.at(x-1,y)!=mask.at(x,y);
                if(edge && start<0)start=y;
                if(!edge && start>=0){edges.add(new Run(x,start,x,y));start=-1;}
            }
        }
    }
}
