package com.cyanrbt.walkbuddy;

/** Constant-size main-thread timing state; no frame, Runnable, or View is retained. */
final class ReadbackTiming {
    static final class Sample {
        final long wallMs,cpuMs,brightnessMs,brightnessCpuMs;
        final boolean running;
        Sample(long wallMs,long cpuMs,long brightnessMs,long brightnessCpuMs,boolean running) {
            this.wallMs=wallMs;this.cpuMs=cpuMs;this.brightnessMs=brightnessMs;
            this.brightnessCpuMs=brightnessCpuMs;this.running=running;
        }
    }
    static final Sample UNKNOWN=new Sample(-1,-1,-1,-1,false);
    private long owner=-1,startWall=-1,startCpu=-1,wallMs=-1,cpuMs=-1,brightnessMs=-1,brightnessCpuMs=-1;
    private boolean running;

    void reset(long generation) {
        owner=generation;running=false;
        startWall=startCpu=wallMs=cpuMs=brightnessMs=brightnessCpuMs=-1;
    }
    void begin(long generation,long wall,long cpu) {
        if(generation!=owner || running)return;
        running=true;startWall=wall;startCpu=cpu;brightnessMs=brightnessCpuMs=-1;
    }
    void brightness(long generation,long fromWall,long fromCpu,long toWall,long toCpu) {
        if(generation!=owner || !running)return;
        brightnessMs=millis(fromWall,toWall);brightnessCpuMs=millis(fromCpu,toCpu);
    }
    void finish(long generation,long wall,long cpu) {
        if(generation!=owner || !running)return;
        wallMs=millis(startWall,wall);cpuMs=millis(startCpu,cpu);running=false;
    }
    Sample snapshot(long generation,long wall,long cpu) {
        if(generation!=owner)return UNKNOWN;
        return new Sample(running?millis(startWall,wall):wallMs,running?millis(startCpu,cpu):cpuMs,
            brightnessMs,brightnessCpuMs,running);
    }
    boolean completedAtLeast(long generation,long millis){return generation==owner && !running && wallMs>=millis;}
    private static long millis(long from,long to){return from>=0 && to>=from?(to-from)/1_000_000:-1;}
}
