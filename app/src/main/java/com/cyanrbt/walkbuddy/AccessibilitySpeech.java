package com.cyanrbt.walkbuddy;

/** Coalesces confirmed reminders; an upgrade outranks a lower-tier cooldown. */
final class AccessibilitySpeech {
    private Detection.Risk observed=Detection.Risk.CAPTURED, lastSpoken=Detection.Risk.CAPTURED;
    private long lastAt=-1;
    private boolean pending,confirmed;

    /** Returns the latest current message, never a stored warning from an older frame. */
    String update(String message,Detection.Risk worst,boolean alert,long now) {
        if(message==null || message.isEmpty() || worst==Detection.Risk.CAPTURED) {
            observed=Detection.Risk.CAPTURED;pending=confirmed=false;return null;
        }
        if(worst!=observed) {
            pending=worst.ordinal()>observed.ordinal();confirmed=false;observed=worst;
        }
        if(alert){pending=true;confirmed=true;}
        if(!pending)return null;
        boolean upgrade=worst.ordinal()>lastSpoken.ordinal();
        long quiet=confirmed?1500:4000;
        if(!upgrade && lastAt>=0 && now-lastAt<quiet)return null;
        lastAt=now;lastSpoken=worst;pending=confirmed=false;
        return message;
    }

    void clear() {
        observed=lastSpoken=Detection.Risk.CAPTURED;lastAt=-1;pending=confirmed=false;
    }
}
