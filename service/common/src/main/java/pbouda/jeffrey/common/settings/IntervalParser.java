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

package pbouda.jeffrey.common.settings;

import java.time.Duration;

public class IntervalParser {

    public static final Duration ASYNC_PROFILER_DEFAULT = Duration.ofMillis(10);

    public static Duration parse(String interval) {
        if (interval == null || interval.isEmpty()) {
            return Duration.ZERO;
        }

        if (interval.endsWith("ms")) {
            return Duration.ofMillis(parseLong(interval, "ms"));
        } else if (interval.endsWith("s")) {
            return Duration.ofSeconds(parseLong(interval, "s"));
        } else if (interval.endsWith("us")) {
            return Duration.ofNanos(parseLong(interval, "us") * 1000);
        } else {
            throw new IllegalArgumentException("Invalid interval format: " + interval);
        }
    }

    private static long parseLong(String interval, String unit) {
        String substring = interval.substring(0, interval.length() - unit.length()).trim();
        return Long.parseLong(substring);
    }
}
