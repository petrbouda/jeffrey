package pbouda.jeffrey.common;

import java.time.Duration;

public abstract class DurationFormatter {

    public static String format(long duration) {
        String formatted = "";

        Duration d = Duration.ofNanos(duration);
        if (d.toDaysPart() > 0) {
            formatted += d.toDaysPart() + "d ";
        }
        if (d.toHoursPart() > 0) {
            formatted += d.toHoursPart() + "h ";
        }
        if (d.toMinutesPart() > 0) {
            formatted += d.toMinutesPart() + "m ";
        }
        if (d.toSecondsPart() > 0) {
            formatted += d.toSecondsPart() + "s ";
        }
        if (d.toMillisPart() > 0) {
            formatted += d.toMillisPart() + "ms ";
        }
        return formatted;
    }
}
