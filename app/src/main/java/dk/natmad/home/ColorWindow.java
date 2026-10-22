package dk.natmad.home;

/** A saved color window never gains time when the clock moves backwards. */
final class ColorWindow {
    static final long DURATION = 12 * 60 * 60 * 1000L;

    static long remaining(long wallDeadline, long elapsedDeadline, int savedBoot,
                          long wallNow, long elapsedNow, int currentBoot) {
        if (wallDeadline <= 0 || elapsedDeadline <= 0) return 0;
        long remaining = Math.min(DURATION, wallDeadline - wallNow);
        if (savedBoot == currentBoot) remaining = Math.min(remaining, elapsedDeadline - elapsedNow);
        // After a reboot, a backwards clock cannot establish a trustworthy deadline.
        else if (wallDeadline - wallNow > DURATION) return 0;
        return Math.max(0, remaining);
    }
}
