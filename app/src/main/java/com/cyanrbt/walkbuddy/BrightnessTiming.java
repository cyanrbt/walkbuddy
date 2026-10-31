package com.cyanrbt.walkbuddy;

/** Immutable scalar diagnostics; no image, callback, Surface, or Activity is retained. */
final class BrightnessTiming {
    enum Stage { UNKNOWN, WAITING, QUEUED, COPYING, DELIVERING, FINISHED }
    static final class Sample {
        final Stage stage;
        final long frameAgeMs,queueMs,wallMs,cpuMs,deliveryMs;
        final int status;
        Sample(Stage stage,long frameAgeMs,long queueMs,long wallMs,long cpuMs,long deliveryMs,int status) {
            this.stage=stage;this.frameAgeMs=frameAgeMs;this.queueMs=queueMs;this.wallMs=wallMs;
            this.cpuMs=cpuMs;this.deliveryMs=deliveryMs;this.status=status;
        }
    }
    static final Sample UNKNOWN=new Sample(Stage.UNKNOWN,-1,-1,-1,-1,-1,-1);
}
