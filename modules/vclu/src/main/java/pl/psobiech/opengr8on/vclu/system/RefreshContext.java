package pl.psobiech.opengr8on.vclu.system;

import pl.psobiech.opengr8on.util.RandomUtil;

public class RefreshContext {
    private final static long REFRESH_NEVER = Long.MAX_VALUE;

    private final boolean polling;

    private final Runnable scheduledFunction;

    private long nextRefreshAfter = System.currentTimeMillis();

    public RefreshContext(boolean polling, Runnable scheduledFunction) {
        this.polling = polling;
        this.scheduledFunction = scheduledFunction;
    }

    public void runIfScheduled() {
        final boolean shouldRefresh = shouldRefresh();
        if (shouldRefresh) {
            if (polling) {
                scheduleNextRefreshRandomized();
            } else {
                disableRefresh();
            }

            scheduledFunction.run();
        }
    }

    public boolean shouldRefresh() {
        final long now = System.currentTimeMillis();

        return nextRefreshAfter < now;
    }

    public void disableRefresh() {
        nextRefreshAfter = REFRESH_NEVER;
    }

    public void scheduleNextRefreshNow() {
        nextRefreshAfter = 0;
    }

    public void scheduleNextRefreshRandomized() {
        final long now = System.currentTimeMillis();

        nextRefreshAfter = getNextRefreshAtRandomized(nextRefreshAfter, now);
    }

    private long getNextRefreshAtRandomized(long previousRefreshAt, long now) {
        return getNextRefreshAt(previousRefreshAt, now, (45_000 + RandomUtil.integer(30_000))); // 45 - 75s
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
