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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class JobDescriptorUtils {

    private static final Map<String, ChronoUnit> CHRONO_UNITS = Arrays.stream(ChronoUnit.values())
            .collect(Collectors.toMap(ChronoUnit::toString, Function.identity()));

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

    public static ChronoUnit resolveChronoUnit(Map<String, String> params, String name) {
        String value = resolveString(params, name);
        ChronoUnit chronoUnit = CHRONO_UNITS.get(value);
        if (chronoUnit == null) {
            throw new IllegalArgumentException("Unknown time unit: " + value);
        }
        return chronoUnit;
    }
}
