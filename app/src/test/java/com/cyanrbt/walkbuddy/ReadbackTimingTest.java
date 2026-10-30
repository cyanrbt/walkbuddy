package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReadbackTimingTest {
    private static final long MS=1_000_000;
    @Test public void runningFailureSnapshotIncludesElapsedTaskAndFinishedBrightnessCopy() {
        ReadbackTiming timing=new ReadbackTiming();timing.reset(3);timing.begin(3,100*MS,20*MS);
        timing.brightness(3,105*MS,22*MS,140*MS,26*MS);
        ReadbackTiming.Sample failure=timing.snapshot(3,145*MS,27*MS);
        assertTrue(failure.running);assertEquals(45,failure.wallMs);assertEquals(7,failure.cpuMs);
        assertEquals(35,failure.brightnessMs);assertEquals(4,failure.brightnessCpuMs);
        assertFalse(timing.completedAtLeast(3,1));
        timing.finish(3,150*MS,28*MS);assertTrue(timing.completedAtLeast(3,50));
        assertFalse(timing.completedAtLeast(3,51));assertFalse(timing.completedAtLeast(4,1));timing.reset(4);
        assertTrue(failure.running);assertEquals(45,failure.wallMs);assertEquals(35,failure.brightnessMs);
    }
    @Test public void newGenerationRejectsRetiredCompletionAndBrightnessMeasurements() {
        ReadbackTiming timing=new ReadbackTiming();timing.reset(1);timing.begin(1,10*MS,2*MS);
        timing.reset(2);timing.begin(1,10*MS,2*MS);timing.finish(1,50*MS,8*MS);
        timing.brightness(1,0,0,40*MS,8*MS);
        ReadbackTiming.Sample empty=timing.snapshot(2,60*MS,10*MS);
        assertFalse(empty.running);assertEquals(-1,empty.wallMs);assertEquals(-1,empty.brightnessMs);
        assertSame(ReadbackTiming.UNKNOWN,timing.snapshot(1,60*MS,10*MS));
        timing.begin(2,100*MS,20*MS);timing.finish(1,900*MS,100*MS);
        assertTrue(timing.snapshot(2,110*MS,21*MS).running);
        timing.finish(2,120*MS,22*MS);assertEquals(20,timing.snapshot(2,900*MS,100*MS).wallMs);
    }
    @Test public void zeroIsValidAndAReverseClockIsUnavailable() {
        ReadbackTiming timing=new ReadbackTiming();timing.reset(0);timing.begin(0,0,0);
        timing.brightness(0,0,0,0,0);timing.finish(0,0,0);
        ReadbackTiming.Sample zero=timing.snapshot(0,5*MS,5*MS);
        assertEquals(0,zero.wallMs);assertEquals(0,zero.cpuMs);assertEquals(0,zero.brightnessMs);
        timing.begin(0,10*MS,10*MS);timing.brightness(0,10*MS,10*MS,9*MS,9*MS);
        timing.finish(0,9*MS,9*MS);ReadbackTiming.Sample invalid=timing.snapshot(0,0,0);
        assertEquals(-1,invalid.wallMs);assertEquals(-1,invalid.cpuMs);assertEquals(-1,invalid.brightnessMs);
    }
}
