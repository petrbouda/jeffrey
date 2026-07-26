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

    public static ChronoUnit resolveChronoUnit(Map<String, String> params, String name) {
        String value = resolveString(params, name);
        ChronoUnit chronoUnit = CHRONO_UNITS.get(value);
        if (chronoUnit == null) {
            throw new IllegalArgumentException("Unknown time unit: " + value);
        }
        return chronoUnit;
    }
}
