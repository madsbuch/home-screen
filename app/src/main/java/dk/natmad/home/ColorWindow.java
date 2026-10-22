package dk.natmad.home;

/** Colors are valid only while the saved switch-on time is less than 12 hours old. */
final class ColorWindow {
    static final long DURATION = 12 * 60 * 60 * 1000L;

    static long remaining(long enabledAt, long now) {
        if (enabledAt <= 0 || enabledAt > now) return 0;
        long age = now - enabledAt;
        return age >= DURATION ? 0 : DURATION - age;
    }
}
