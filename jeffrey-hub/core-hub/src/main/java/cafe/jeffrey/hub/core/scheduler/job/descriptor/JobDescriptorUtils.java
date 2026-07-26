/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.hub.core.scheduler.job.descriptor;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class JobDescriptorUtils {

    private static final Map<String, ChronoUnit> CHRONO_UNITS = Arrays.stream(ChronoUnit.values())
            .collect(Collectors.toMap(ChronoUnit::toString, Function.identity()));

    /**
     * Size suffixes accepted by {@link #resolveBytes}, mapped to their multiplier.
     * Binary units throughout — {@code 1G} is 1024³ bytes, matching how disk budgets
     * are reasoned about operationally.
     */
    private static final Map<Character, Long> SIZE_MULTIPLIERS = Map.of(
            'K', 1024L,
            'M', 1024L * 1024L,
            'G', 1024L * 1024L * 1024L,
            'T', 1024L * 1024L * 1024L * 1024L);

    public static String resolveString(Map<String, String> params, String name) {
        String value = params.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static int resolveInt(Map<String, String> params, String name) {
        String value = resolveString(params, name);
        return Integer.parseInt(value);
    }

    public static long resolveLong(Map<String, String> params, String name) {
        String value = resolveString(params, name);
        return Long.parseLong(value);
    }

    /**
     * Resolves a byte size written either as a plain number of bytes ({@code 1048576})
     * or with a binary unit suffix ({@code 512K}, {@code 100M}, {@code 20G}, {@code 2T}).
     *
     * @throws IllegalArgumentException if the value is malformed or not positive
     */
    public static long resolveBytes(Map<String, String> params, String name) {
        String value = resolveString(params, name).trim().toUpperCase(Locale.ROOT);

        char suffix = value.charAt(value.length() - 1);
        Long multiplier = SIZE_MULTIPLIERS.get(suffix);

        long bytes;
        try {
            if (multiplier == null) {
                bytes = Long.parseLong(value);
            } else {
                bytes = Long.parseLong(value.substring(0, value.length() - 1).trim()) * multiplier;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " is not a valid size: " + value, e);
        }

        if (bytes <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return bytes;
    }

    /**
     * Resolves a required duration param written in the operator-friendly notation used in
     * {@code scheduler-defaults.properties} ({@code 30s}, {@code 15m}, {@code 1h}, {@code 7d})
     * or in ISO-8601.
     */
    public static Duration resolveDuration(Map<String, String> params, String name) {
        String value = resolveString(params, name);
        try {
            return parseDuration(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " is not a valid duration: " + value, e);
        }
    }

    /**
     * Parses the shorthand duration notation accepted in scheduler configuration
     * ({@code 30s}, {@code 15m}, {@code 1h}, {@code 7d}) by translating it to ISO-8601 first.
     * A bare number is read as seconds.
     */
    public static Duration parseDuration(String value) {
        String trimmed = value.trim().toLowerCase(Locale.ROOT);

        if (trimmed.startsWith("p") || trimmed.startsWith("-p")) {
            return Duration.parse(value.trim().toUpperCase(Locale.ROOT));
        }

        String magnitude = trimmed.substring(0, Math.max(0, trimmed.length() - 1));
        char suffix = trimmed.isEmpty() ? ' ' : trimmed.charAt(trimmed.length() - 1);
        String iso = switch (suffix) {
            case 'd' -> "P" + magnitude + "D";
            case 'h' -> "PT" + magnitude + "H";
            case 'm' -> "PT" + magnitude + "M";
            case 's' -> "PT" + magnitude + "S";
            default -> "PT" + trimmed + "S";
        };

        return Duration.parse(iso);
    }

    public static ChronoUnit resolveChronoUnit(Map<String, String> params, String name) {
        String value = resolveString(params, name);
        ChronoUnit chronoUnit = CHRONO_UNITS.get(value);
        if (chronoUnit == null) {
            throw new IllegalArgumentException("Unknown time unit: " + value);
        }
        return chronoUnit;
    }
}
