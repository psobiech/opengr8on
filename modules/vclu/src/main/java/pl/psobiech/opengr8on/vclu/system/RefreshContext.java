package pl.psobiech.opengr8on.vclu.system;

import pl.psobiech.opengr8on.util.RandomUtil;

import java.util.concurrent.TimeUnit;

public class RefreshContext {
    private static final int RARE_MILLIS = (int) TimeUnit.HOURS.toMillis(6);

    private static final int RANDOMIZATION_MILLIS = 30_000;

    private final boolean polling;

    private final Runnable scheduledFunction;

    private long nextRefreshAfter;

    public RefreshContext(boolean polling, Runnable scheduledFunction) {
        this.polling = polling;
        this.scheduledFunction = scheduledFunction;

        scheduleNextRefreshNow();
    }

    public void runIfScheduled() {
        final boolean shouldRefresh = shouldRefresh();
        if (shouldRefresh) {
            if (polling) {
                scheduleNextRefreshRandomized();
            } else {
                scheduleNextRefreshRarely();
            }

            scheduledFunction.run();
        }
    }

    public boolean shouldRefresh() {
        final long now = System.currentTimeMillis();

        return nextRefreshAfter < now;
    }

    public void scheduleNextRefreshRarely() {
        final long now = System.currentTimeMillis();

        nextRefreshAfter = getNextRefreshAtRandomized(nextRefreshAfter, now, RARE_MILLIS, RANDOMIZATION_MILLIS);
    }

    public void scheduleNextRefreshNow() {
        nextRefreshAfter = 0;
    }

    public void scheduleNextRefreshRandomized() {
        scheduleNextRefreshRandomized(45_000);
    }

    public void scheduleNextRefreshRandomized(int minimumDelay) {
        final long now = System.currentTimeMillis();

        nextRefreshAfter = getNextRefreshAtRandomized(nextRefreshAfter, now, minimumDelay, RANDOMIZATION_MILLIS);
    }

    private long getNextRefreshAtRandomized(long previousRefreshAt, long now, int minimum, int delta) {
        return getNextRefreshAt(previousRefreshAt, now, (minimum + RandomUtil.integer(delta)));
    }

    public void scheduleNextRefreshIn(long duration) {
        nextRefreshAfter = getNextRefreshAt(nextRefreshAfter, System.currentTimeMillis(), duration);
    }

    private long getNextRefreshAt(long previousRefreshAt, long now, long duration) {
        final long nextRefreshAfterCandidate = now + duration;
        if (previousRefreshAt > now) {
            // if another trigger tries to schedule refresh later than the current schedule
            // we ignore it as it could starve the refresh itself
            return Math.min(nextRefreshAfterCandidate, previousRefreshAt);
        }

        return nextRefreshAfterCandidate;
    }
}
