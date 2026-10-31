package com.cyanrbt.walkbuddy;

/** Main-thread brightness cadence and ownership, independent of Android and pixel storage. */
final class BrightnessFlight {
    static final long PERIOD_MS=1000,TIMEOUT_MS=1000;
    enum Outcome { ACCEPTED, EXPIRED, RETIRED, UNRELATED }
    static final class Ticket {
        final long frameAt,dueAt;
        private Ticket(long frameAt,long dueAt){this.frameAt=frameAt;this.dueAt=dueAt;}
    }
    private Ticket pending;
    private long submittedAt=-1,dueAt=-1;
    private boolean closed;

    void observe(long frameAt) {
        if(closed || pending!=null || frameAt<0 || dueAt>=0)return;
        if(submittedAt<0 || frameAt>=submittedAt && frameAt-submittedAt>=PERIOD_MS)dueAt=frameAt;
    }
    boolean due(){return !closed && pending==null && dueAt>=0;}
    long dueAt(){return dueAt;}
    boolean pending(){return pending!=null;}
    boolean expired(long now) {
        if(closed)return false;
        long at=pending!=null?pending.dueAt:dueAt;
        return at>=0 && (pending!=null && now<pending.frameAt || now<at || now-at>=TIMEOUT_MS);
    }
    Ticket begin(long frameAt,long now) {
        if(!due() || frameAt<dueAt || frameAt>now || now-frameAt>=TIMEOUT_MS || expired(now))return null;
        pending=new Ticket(frameAt,dueAt);submittedAt=frameAt;dueAt=-1;return pending;
    }
    Outcome complete(Ticket ticket,long now) {
        if(ticket==null || ticket!=pending)return Outcome.UNRELATED;
        boolean stale=now<ticket.frameAt || now-ticket.dueAt>=TIMEOUT_MS;
        pending=null;
        return closed?Outcome.RETIRED:stale?Outcome.EXPIRED:Outcome.ACCEPTED;
    }
    void close(){closed=true;dueAt=-1;}
}
