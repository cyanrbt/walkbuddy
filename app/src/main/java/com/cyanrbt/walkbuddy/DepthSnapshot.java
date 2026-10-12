package com.cyanrbt.walkbuddy;

import java.util.*;

final class DepthSnapshot {
    final long sampledAt;
    final List<Detection> detections;
    final String status;
    DepthSnapshot(long sampledAt,List<Detection> detections,String status){this.sampledAt=sampledAt;this.detections=Collections.unmodifiableList(new ArrayList<>(detections));this.status=status;}
    List<Detection> rotated(int degrees){List<Detection> out=new ArrayList<>();for(Detection d:detections)out.add(d.rotate(degrees));return out;}
}
