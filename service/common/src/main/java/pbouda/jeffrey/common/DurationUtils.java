/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.common;

import java.time.Duration;

/**
 * Utility class for parsing duration strings into {@link Duration} objects.
 */
public abstract class DurationUtils {

    /**
     * Parses a string representation of duration into a {@link Duration} object.
     * Supported formats:
     * - "X ns" (nanoseconds)
     * - "X μs" or "X us" (microseconds)
     * - "X ms" (milliseconds)
     * - "X s" (seconds)
     * - "X m" (minutes)
     * - "X h" (hours)
     * - "X d" (days)
     *
     * @param durationStr the string to parse, e.g. "1000 ms"
     * @return the parsed {@link Duration}
     * @throws IllegalArgumentException if the string format is invalid
     */
    public static Duration parse(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be null or empty");
        }

        durationStr = durationStr.trim();

        // Find the last space to separate value and unit
        int lastSpaceIndex = durationStr.lastIndexOf(' ');
        if (lastSpaceIndex <= 0) {
            throw new IllegalArgumentException("Invalid duration format: " + durationStr +
                                               ". Expected format: '<number> <unit>' where unit is one of ns, μs, us, ms, s, m, h, d");
        }

        String valueStr = durationStr.substring(0, lastSpaceIndex).trim();
        String unit = durationStr.substring(lastSpaceIndex + 1).trim();

        long value;
        try {
            value = Long.parseLong(valueStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in duration: " + valueStr);
        }

        return switch (unit) {
            case "ns" -> Duration.ofNanos(value);
            case "μs", "us" -> Duration.ofNanos(value * 1_000);
            case "ms" -> Duration.ofMillis(value);
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            default -> throw new IllegalArgumentException("Unknown time unit: " + unit +
                                                          ". Supported units are: ns, μs, us, ms, s, m, h, d");
        };
    }

    /**
     * Attempts to parse a string representation of duration into a {@link Duration} object.
     * Returns the default value if parsing fails.
     *
     * @param durationStr  the string to parse, e.g. "1000 ms"
     * @param defaultValue the default value to return if parsing fails
     * @return the parsed {@link Duration} or the default value if parsing fails
     */
    public static Duration parseOrDefault(String durationStr, Duration defaultValue) {
        try {
            return parse(durationStr);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    public static String format(Duration d) {
        String formatted = "";

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
        if (d.toNanosPart() >= 1000) {
            formatted += ((d.toNanosPart() / 1000) % 1000) + "µs ";
        }
        if (d.toNanosPart() > 0) {
            formatted += (d.toNanosPart() % 1000) + "ns";
        }
        return formatted;
    }

    public static String formatNanos(long duration) {
        return format(Duration.ofNanos(duration));
    }

    /**
     * Formats a Duration object into a string representation showing only the two most significant parts.
     * For example, "2d 5h" or "3m 45s" instead of showing all non-zero duration parts.
     *
     * @param d the Duration to format
     * @return a string with only the two most significant parts of the duration
     */
    public static String format2parts(Duration d) {
        String formatted = format(d);
        String[] parts = formatted.split(" ");
        if (parts.length <= 2) {
            return formatted; // Already has 2 or fewer parts
        } else {
            // Join only the first two parts
            return parts[0] + " " + parts[1];
        }
    }
}
