package com.doctorhoney.bedswitching;

import java.util.concurrent.TimeUnit;

/** Per player instance, transient and accessed only on the server thread. */
final class RequestLimits {
    enum Action { RESPAWN, REMOVE, HISTORY, RENAME }

    private static final long INTERVAL = TimeUnit.MILLISECONDS.toNanos(100);
    private final long[] nextSlot = new long[Action.values().length];
    private final boolean[] started = new boolean[Action.values().length];

    private boolean reportedBusy;
    private long lastBusy;

    boolean canReportBusy(long now) {
        if (reportedBusy && now - lastBusy < TimeUnit.SECONDS.toNanos(1)) return false;
        reportedBusy = true;
        lastBusy = now;
        return true;
    }

    boolean accept(Action action, long now) {
        int i = action.ordinal();
        int burst = action == Action.RESPAWN ? 2 : 12;
        long debt = started[i] ? Math.max(0, nextSlot[i] - now) : 0;
        if (debt > (burst - 1) * INTERVAL) return false;
        nextSlot[i] = now + debt + INTERVAL;
        started[i] = true;
        return true;
    }
}
