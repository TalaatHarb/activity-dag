package net.talaatharb.activitydag.model;

/** Unit in which an activity's duration is expressed. Planning uses continuous calendar time (24h days). */
public enum DurationUnit {
    MINUTES("minute(s)", 1),
    HOURS("hour(s)", 60),
    DAYS("day(s)", 60 * 24),
    WEEKS("week(s)", 60 * 24 * 7);

    private final String label;
    private final long minutes;

    DurationUnit(String label, long minutes) {
        this.label = label;
        this.minutes = minutes;
    }

    public long toMinutes(long amount) {
        return Math.multiplyExact(amount, minutes);
    }

    public long minutes() {
        return minutes;
    }

    /** Compact human readable form of a minute count, e.g. "1d 2h 30m". */
    public static String format(long totalMinutes) {
        if (totalMinutes <= 0) {
            return "0";
        }
        long days = totalMinutes / DAYS.minutes;
        long hours = totalMinutes % DAYS.minutes / HOURS.minutes;
        long mins = totalMinutes % HOURS.minutes;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (mins > 0) {
            sb.append(mins).append("m");
        }
        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return label;
    }
}
