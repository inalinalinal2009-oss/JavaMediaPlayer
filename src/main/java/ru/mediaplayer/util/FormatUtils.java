package ru.mediaplayer.util;

public final class FormatUtils {
    private FormatUtils() {
    }

    public static String time(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) {
            seconds = 0;
        }
        long total = (long) seconds;
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long secs = total % 60;
        return hours > 0
                ? "%d:%02d:%02d".formatted(hours, minutes, secs)
                : "%02d:%02d".formatted(minutes, secs);
    }
}
