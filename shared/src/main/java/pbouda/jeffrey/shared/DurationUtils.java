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

package pbouda.jeffrey.shared;

import java.time.Duration;

/**
 * Utility class for parsing duration strings into {@link Duration} objects.
 */
public abstract class DurationUtils {

    /**
     * Parses a string representation of duration into a {@link Duration} object.
     * Supported formats:
     * - "X ns" or "Xns" (nanoseconds)
     * - "X μs", "X us", "Xμs", or "Xus" (microseconds)
     * - "X ms" or "Xms" (milliseconds)
     * - "X s" or "Xs" (seconds)
     * - "X m" or "Xm" (minutes)
     * - "X h" or "Xh" (hours)
     * - "X d" or "Xd" (days)
     *
     * @param durationStr the string to parse, e.g. "1000 ms" or "1000ms"
     * @return the parsed {@link Duration}
     * @throws IllegalArgumentException if the string format is invalid
     */
    public static Duration parse(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be null or empty");
        }

        durationStr = durationStr.trim();

        String valueStr;
        String unit;

        // Find the last space to separate value and unit
        int lastSpaceIndex = durationStr.lastIndexOf(' ');
        
        if (lastSpaceIndex > 0) {
            // Format with space: "1000 ms"
            valueStr = durationStr.substring(0, lastSpaceIndex).trim();
            unit = durationStr.substring(lastSpaceIndex + 1).trim();
        } else {
            // Format without space: "1000ms"
            // Find the position where the numeric part ends and the unit begins
            int unitStartIndex = findUnitStartIndex(durationStr);
            
            if (unitStartIndex <= 0 || unitStartIndex >= durationStr.length()) {
                throw new IllegalArgumentException("Invalid duration format: " + durationStr +
                                                  ". Expected format: '<number><unit>' or '<number> <unit>' where unit is one of ns, μs, us, ms, s, m, h, d");
            }
            
            valueStr = durationStr.substring(0, unitStartIndex).trim();
            unit = durationStr.substring(unitStartIndex).trim();
        }

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

    public static String formatNanos2Units(long duration) {
        return format2parts(Duration.ofNanos(duration));
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
    
    /**
     * Helper method to find the index where the unit starts in a duration string without spaces.
     * For example, in "1000ms", it would return the index of 'm'.
     *
     * @param durationStr the duration string without spaces
     * @return the index where the unit starts, or -1 if no valid unit is found
     */
    private static int findUnitStartIndex(String durationStr) {
        // Check for each possible unit from longest to shortest to avoid ambiguity
        String[] possibleUnits = {"μs", "us", "ms", "ns", "s", "m", "h", "d"};
        
        for (int i = 0; i < durationStr.length(); i++) {
            // Skip if we're still in the numeric part
            if (Character.isDigit(durationStr.charAt(i))) {
                continue;
            }
            
            // Check if the substring from this position matches any of our units
            String remainingStr = durationStr.substring(i);
            for (String unit : possibleUnits) {
                if (remainingStr.equals(unit)) {
                    return i;
                }
            }
            
            // If we found a non-digit character but it's not the start of a valid unit,
            // we'll return this position anyway and let the unit validation handle the error
            return i;
        }
        
        return -1; // No unit found
    }
}
