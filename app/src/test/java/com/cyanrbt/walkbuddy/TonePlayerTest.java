package com.cyanrbt.walkbuddy;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TonePlayerTest {
    private static final class Rig implements AutoCloseable {
        final AtomicLong now=new AtomicLong(100);
        final AtomicInteger creates=new AtomicInteger(),plays=new AtomicInteger(),stops=new AtomicInteger(),closes=new AtomicInteger(),failures=new AtomicInteger();
        final List<Thread> threads=Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch entered=new CountDownLatch(1);
        volatile CountDownLatch createGate,closeGate;
        volatile boolean createFails,playFails;
        final TonePlayer player=new TonePlayer(now::get,()->{
            threads.add(Thread.currentThread());creates.incrementAndGet();entered.countDown();await(createGate);
            if(createFails)throw new IllegalStateException("No audio resource");
            return new TonePlayer.Output(){
                public boolean play(){threads.add(Thread.currentThread());plays.incrementAndGet();return !playFails;}
                public void stop(){threads.add(Thread.currentThread());stops.incrementAndGet();}
                public void close(){threads.add(Thread.currentThread());await(closeGate);closes.incrementAndGet();}
            };
        });
        TonePlayer.Session session(){return new TonePlayer.Session(new InferenceWorker.Ticket(now.get()),(s,e)->failures.incrementAndGet());}
        void ready() throws Exception {assertTrue(entered.await(2,TimeUnit.SECONDS));}
        void idle() throws Exception {assertTrue("Audio worker did not settle",player.awaitIdle(2000));}
        public void close() throws Exception {
            if(createGate!=null)createGate.countDown();if(closeGate!=null)closeGate.countDown();
            player.shutdown();idle();
            for(Thread thread:threads){assertNotSame(Thread.currentThread(),thread);assertSame(threads.get(0),thread);}
        }
    }
    private static void await(CountDownLatch latch) {
        if(latch==null)return;
        try{if(!latch.await(4,TimeUnit.SECONDS))throw new IllegalStateException("Probe gate timed out");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
    @Test public void preparesAndReusesOneBackgroundOutputThenReleasesOnCancel() throws Exception {
        try(Rig r=new Rig()) {
            TonePlayer.Session s=r.session();assertTrue(r.player.prepare(s));r.idle();
            assertEquals(0,r.plays.get());
            assertTrue(r.player.play(s,500));r.idle();assertTrue(r.player.play(s,500));r.idle();
            assertEquals(1,r.creates.get());assertEquals(2,r.plays.get());
            r.player.cancel(s);r.idle();assertEquals(1,r.closes.get());assertNull(s.failure);
            assertFalse(s.frames.cancelled);
        }
    }
    @Test public void cancellationReturnsWhileNativeInitializationIsBlockedAndPreventsLatePlayback() throws Exception {
        try(Rig r=new Rig()) {
            r.createGate=new CountDownLatch(1);TonePlayer.Session s=r.session();
            r.player.play(s,500);r.ready();r.player.cancel(s);
            assertEquals(1,r.createGate.getCount());assertTrue(s.cancelled);assertFalse(s.frames.cancelled);
            r.createGate.countDown();r.idle();assertEquals(0,r.plays.get());assertEquals(1,r.closes.get());
        }
    }
    @Test public void queuedExpiredCueIsDroppedAndWarmOutputCanServeANewFreshCue() throws Exception {
        try(Rig r=new Rig()) {
            r.createGate=new CountDownLatch(1);TonePlayer.Session s=r.session();
            r.player.prepare(s);r.ready();r.player.play(s,200);r.now.set(201);
            r.createGate.countDown();r.idle();assertEquals(0,r.plays.get());assertEquals(0,r.closes.get());
            assertTrue(r.player.play(s,600));r.idle();assertEquals(1,r.plays.get());assertEquals(1,r.creates.get());
        }
    }
    @Test public void riskDowngradeDiscardsQueuedCueAndStopsWithoutClosingWarmOutput() throws Exception {
        try(Rig r=new Rig()) {
            r.createGate=new CountDownLatch(1);TonePlayer.Session s=r.session();
            r.player.observe(s,Detection.Risk.NEAR);r.player.play(s,600);r.ready();
            r.player.observe(s,Detection.Risk.CAUTION);r.createGate.countDown();r.idle();
            assertEquals(0,r.plays.get());assertEquals(1,r.stops.get());assertEquals(0,r.closes.get());
            r.player.observe(s,Detection.Risk.CAUTION);r.player.observe(s,Detection.Risk.NEAR);r.idle();
            assertEquals(1,r.stops.get());assertEquals(0,r.plays.get());
        }
    }
    @Test public void disappearingThenReturningRiskCannotReviveAnOldCue() throws Exception {
        try(Rig r=new Rig()) {
            r.createGate=new CountDownLatch(1);TonePlayer.Session s=r.session();
            r.player.observe(s,Detection.Risk.NEAR);r.player.play(s,600);r.ready();
            r.player.observe(s,Detection.Risk.CAPTURED);r.player.observe(s,Detection.Risk.NEAR);
            r.createGate.countDown();r.idle();assertEquals(0,r.plays.get());
            r.player.play(s,600);r.idle();assertEquals(1,r.plays.get());
        }
    }
    @Test public void onlyNewestPendingCueSurvivesABlockedInitializer() throws Exception {
        try(Rig r=new Rig()) {
            r.createGate=new CountDownLatch(1);TonePlayer.Session s=r.session();r.player.prepare(s);r.ready();
            for(int i=0;i<100;i++){assertTrue(r.player.play(s,600));assertEquals(1,r.player.pendingCount());}
            r.createGate.countDown();r.idle();assertEquals(1,r.plays.get());assertEquals(1,r.creates.get());
        }
    }
    @Test public void cancellingAnOldSessionDoesNotCloseTheNewOneOrCancelInference() throws Exception {
        try(Rig r=new Rig()) {
            TonePlayer.Session old=r.session(),next=r.session();r.player.prepare(old);r.idle();
            r.player.play(next,600);r.idle();assertEquals(1,r.closes.get());
            r.player.cancel(old);r.idle();assertEquals(1,r.closes.get());
            r.player.play(next,600);r.idle();assertEquals(2,r.plays.get());assertFalse(next.frames.cancelled);
        }
    }
    @Test public void initializationFailureReportsOnceAndRequiresANewUserSessionToRetry() throws Exception {
        try(Rig r=new Rig()) {
            r.createFails=true;TonePlayer.Session s=r.session();r.player.prepare(s);r.idle();
            assertTrue(s.failed);assertNull(s.failure);assertEquals(1,r.failures.get());
            assertFalse(r.player.prepare(s));assertFalse(r.player.play(s,600));assertEquals(1,r.creates.get());
            r.createFails=false;r.player.play(r.session(),600);r.idle();assertEquals(1,r.plays.get());
        }
    }
    @Test public void rejectedPlaybackClosesOutputAndReportsFailure() throws Exception {
        try(Rig r=new Rig()) {
            r.playFails=true;TonePlayer.Session s=r.session();r.player.play(s,600);r.idle();
            assertTrue(s.failed);assertEquals(1,r.failures.get());assertEquals(1,r.closes.get());
        }
    }
    @Test public void blockedReleaseDoesNotHoldControlLockOrLetAnOldCancellationCloseNewOutput() throws Exception {
        try(Rig r=new Rig()) {
            TonePlayer.Session old=r.session();r.player.prepare(old);r.idle();r.closeGate=new CountDownLatch(1);
            r.player.cancel(old);TonePlayer.Session next=r.session();assertTrue(r.player.play(next,600));
            assertEquals(1,r.closeGate.getCount());r.closeGate.countDown();r.idle();
            assertEquals(1,r.closes.get());assertEquals(2,r.creates.get());assertEquals(1,r.plays.get());
        }
    }
    @Test public void expiredFramesRejectRequestsAndCannotPlayAfterInitialization() throws Exception {
        try(Rig r=new Rig()) {
            r.createGate=new CountDownLatch(1);TonePlayer.Session s=r.session();r.player.play(s,2000);r.ready();
            r.now.set(1301);r.createGate.countDown();r.idle();
            assertEquals(0,r.plays.get());assertEquals(1,r.closes.get());assertFalse(r.player.prepare(s));
        }
    }
    @Test public void shutdownWaitsForReleaseAndRejectsNewWork() throws Exception {
        try(Rig r=new Rig()) {
            TonePlayer.Session s=r.session();r.player.prepare(s);r.idle();r.closeGate=new CountDownLatch(1);
            r.player.shutdown();assertFalse(r.player.awaitIdle(20));assertFalse(r.player.play(r.session(),600));
            r.closeGate.countDown();r.idle();assertEquals(1,r.closes.get());
        }
    }
}
