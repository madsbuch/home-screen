package dk.natmad.home;

public final class ColorWindowTest {
    public static void main(String[] args) {
        long start = 1_000_000_000L;
        long elapsed = 50_000L;
        long duration = ColorWindow.DURATION;
        check(ColorWindow.remaining(0, 0, -2, start, elapsed, 1) == 0, "default monochrome");
        check(ColorWindow.remaining(start + duration, elapsed + duration, 1, start, elapsed, 1) == duration, "12 hours");
        check(ColorWindow.remaining(start + duration, elapsed + duration, 1,
            start + duration - 1, elapsed + duration - 1, 1) == 1, "last millisecond");
        check(ColorWindow.remaining(start + duration, elapsed + duration, 1,
            start + duration, elapsed + duration, 1) == 0, "expiry boundary");
        check(ColorWindow.remaining(start + duration, elapsed + duration, 1,
            start - 10_000, elapsed + duration, 1) == 0, "backward clock cannot extend window");
        check(ColorWindow.remaining(start + duration, elapsed + duration, 1,
            start + duration + 1000, elapsed + 1000, 1) == 0, "forward clock expires");
        check(ColorWindow.remaining(start + duration, elapsed + duration, 1,
            start + 1000, 100, 2) == duration - 1000, "reboot keeps deadline");
        check(ColorWindow.remaining(start + duration, elapsed + duration, 1,
            start + duration, 100, 2) == 0, "reboot after expiry");
        check(ColorWindow.remaining(start + duration, elapsed + duration, 1,
            start - 1000, 100, 2) == 0, "reboot with untrustworthy clock defaults to monochrome");
        System.out.println("All color-window checks passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
