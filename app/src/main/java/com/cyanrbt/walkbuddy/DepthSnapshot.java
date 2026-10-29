package com.cyanrbt.walkbuddy;

/** Depth readiness is structured so the UI can guide setup without parsing status prose. */
final class DepthSnapshot {
    enum Stage {
        TRACKING, ORIENT, GROUND_SEARCH, GROUND_HOLD,
        DEPTH_WAIT, DEPTH_SPARSE, DEPTH_STALE, MEASURING
    }
    final long sampledAt;
    final java.util.List<Detection> detections;
    final String status;
    final Stage stage;
    final boolean groundReady;
    final boolean depthReady;
    DepthSnapshot(long sampledAt, java.util.List<Detection> detections, String status,
                  Stage stage, boolean groundReady, boolean depthReady) {
        this.sampledAt = sampledAt;
        this.detections = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(detections));
        this.status = status;
        this.stage = stage;
        this.groundReady = groundReady;
        this.depthReady = depthReady;
    }
    java.util.List<Detection> rotated(int degrees) {
        java.util.List<Detection> out = new java.util.ArrayList<>();
        for (Detection d : detections) out.add(d.rotate(degrees));
        return out;
    }
    boolean fresh(long now) {return now>=sampledAt && now-sampledAt<=500;}
}
