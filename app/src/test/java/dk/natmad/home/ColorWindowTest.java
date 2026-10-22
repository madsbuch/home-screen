package dk.natmad.home;

public final class ColorWindowTest {
    public static void main(String[] args) {
        long start = 1_000_000_000L;
        long duration = ColorWindow.DURATION;
        check(ColorWindow.remaining(0, start) == 0, "default monochrome");
        check(ColorWindow.remaining(start, start) == duration, "new switch-on time");
        check(ColorWindow.remaining(start, start + duration - 1) == 1, "just before 12 hours");
        check(ColorWindow.remaining(start, start + duration) == 0, "12-hour boundary");
        check(ColorWindow.remaining(start, start + duration + 1000) == 0, "older than 12 hours");
        check(ColorWindow.remaining(start, start + 1000) == duration - 1000, "age uses the original timestamp");
        check(ColorWindow.remaining(start, start - 1000) == 0, "future timestamps fall back to monochrome");
        System.out.println("All color timestamp checks passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
