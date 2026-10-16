package com.cyanrbt.walkbuddy;

/**
 * Speaks risk changes and confirmed alerts only. Repeated frames of the same tier stay silent
 * so TalkBack is not flooded while walking.
 */
final class AccessibilitySpeech {
    private String lastTier = "";
    private long lastAt;

    /** @return text to announce now, or null to stay quiet. */
    String update(String message, Detection.Risk worst, boolean alert, long now) {
        if (message == null || message.isEmpty()) return null;
        String tier = worst.name();
        boolean changed = !tier.equals(lastTier);
        boolean escalated = changed && worst != Detection.Risk.CAPTURED;
        if (changed) lastTier = tier;
        if (!alert && !escalated) return null;
        long quiet = alert ? 1500 : 4000;
        if (lastAt != 0 && now - lastAt < quiet) return null;
        lastAt = now;
        return message;
    }

    void clear() {
        lastTier = "";
        lastAt = 0;
    }
}
