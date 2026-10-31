package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;

public class BrightnessFlightTest {
    @Test public void firstObservationStartsOneRequestAndKeepsItsOriginalFrameTime(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);
        BrightnessFlight.Ticket ticket=flight.begin(1000,1200);assertNotNull(ticket);assertEquals(1000,ticket.frameAt);
        flight.observe(1500);assertNull(flight.begin(1500,1500));
        assertFalse(flight.expired(2200));assertTrue(flight.expired(2201));
    }
    @Test public void newerFramesCannotExtendAWaitingRequestDeadline(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);flight.begin(1000,1000);
        for(long frame:new long[]{1100,1400,1700,1999,2200})flight.observe(frame);
        assertTrue(flight.expired(2201));
    }
    @Test public void globalBusyWaitingDoesNotRefreshTheOldestDueFrame(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);flight.observe(1500);
        assertTrue(flight.expired(2201));assertNull(flight.begin(2200,2201));
    }
    @Test public void futureAndTooOldObservationsCannotStartWork(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);
        assertNull(flight.begin(1001,1000));assertFalse(flight.expired(2001));assertNull(flight.begin(1000,2001));
        assertFalse(flight.pending());
    }
    @Test public void completionBeyondThePreviewFreshnessBoundaryIsExpired(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);
        assertEquals(BrightnessFlight.Outcome.EXPIRED,flight.complete(flight.begin(1000,1000),2201));
    }
    @Test public void brightnessCompletionAtThePreviewBoundaryRemainsCurrent(){
        SessionState preview=livePreview();
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);
        BrightnessFlight.Ticket ticket=flight.begin(1000,1000);
        assertTrue(preview.fresh(2200));assertFalse(preview.expired(2200));assertFalse(flight.expired(2200));
        assertEquals(BrightnessFlight.Outcome.ACCEPTED,flight.complete(ticket,2200));
        assertFalse(preview.fresh(2201));assertTrue(preview.expired(2201));
    }
    @Test public void a1004msBrightnessCompletionDoesNotAdmitAnOldModelResult(){
        SessionState preview=livePreview();preview.recognition=true;preview.analysisAt=1000;
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);
        assertTrue(preview.fresh(2004));assertFalse(preview.acceptResult(preview.generation,1000,2004));
        assertEquals(BrightnessFlight.Outcome.ACCEPTED,flight.complete(flight.begin(1000,1000),2004));
    }
    @Test public void thePublished1000msObservationStartBoundaryRemainsInclusive(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);
        assertNotNull(flight.begin(1000,2000));
    }
    @Test public void closeRetainsPendingOwnershipUntilTheActualCompletion(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);BrightnessFlight.Ticket ticket=flight.begin(1000,1000);
        flight.close();assertTrue(flight.pending());assertFalse(flight.expired(9000));
        assertEquals(BrightnessFlight.Outcome.RETIRED,flight.complete(ticket,9000));assertFalse(flight.pending());
        flight.observe(10000);assertFalse(flight.due());assertNull(flight.begin(10000,10000));
    }
    @Test public void anotherOwnerAndDuplicateCallbacksCannotClearANewerTicket(){
        BrightnessFlight a=new BrightnessFlight(),b=new BrightnessFlight();a.observe(1000);b.observe(1000);
        BrightnessFlight.Ticket old=a.begin(1000,1000),current=b.begin(1000,1000);
        assertEquals(BrightnessFlight.Outcome.UNRELATED,b.complete(old,1100));assertTrue(b.pending());
        assertEquals(BrightnessFlight.Outcome.ACCEPTED,b.complete(current,1100));
        b.observe(2000);BrightnessFlight.Ticket next=b.begin(2000,2000);
        assertEquals(BrightnessFlight.Outcome.UNRELATED,b.complete(current,2100));assertTrue(b.pending());
        assertEquals(BrightnessFlight.Outcome.ACCEPTED,b.complete(next,2100));
    }
    @Test public void nextPeriodStartsFromOriginalSubmissionRatherThanCompletion(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);BrightnessFlight.Ticket ticket=flight.begin(1000,1000);
        assertEquals(BrightnessFlight.Outcome.ACCEPTED,flight.complete(ticket,1700));
        flight.observe(1999);assertFalse(flight.due());flight.observe(2000);assertTrue(flight.due());
    }
    @Test public void backwardsClockCannotApproveAPendingRequest(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);BrightnessFlight.Ticket ticket=flight.begin(1000,1000);
        assertTrue(flight.expired(999));assertEquals(BrightnessFlight.Outcome.EXPIRED,flight.complete(ticket,999));
    }
    @Test public void startingWithANewerFrameCannotExtendTheOldestDueDeadline(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);flight.observe(1500);
        BrightnessFlight.Ticket ticket=flight.begin(1500,1500);assertEquals(1500,ticket.frameAt);assertEquals(1000,ticket.dueAt);
        assertFalse(flight.expired(2200));assertTrue(flight.expired(2201));
        assertEquals(BrightnessFlight.Outcome.EXPIRED,flight.complete(ticket,2201));
    }
    @Test public void waitingForTheGlobalSlotCannotCompressTheNextPeriod(){
        BrightnessFlight flight=new BrightnessFlight();flight.observe(1000);
        BrightnessFlight.Ticket ticket=flight.begin(1500,1500);
        assertEquals(BrightnessFlight.Outcome.ACCEPTED,flight.complete(ticket,1600));
        flight.observe(2000);assertFalse(flight.due());flight.observe(2499);assertFalse(flight.due());
        flight.observe(2500);assertTrue(flight.due());
    }
    private static SessionState livePreview(){
        SessionState preview=new SessionState();preview.start(1000);preview.capture(1,1000);preview.display(1000);
        assertTrue(preview.promote(1000));return preview;
    }
}
