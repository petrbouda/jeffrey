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

package pbouda.jeffrey.common.time;

import pbouda.jeffrey.common.ProfilingStartEnd;

import java.time.Duration;
import java.time.Instant;

public record AbsoluteTimeRange(Instant start, Instant end) implements TimeRange {

    public static final AbsoluteTimeRange UNLIMITED = new AbsoluteTimeRange();

    public AbsoluteTimeRange(long startInMillis, long endInMillis) {
        this(Instant.ofEpochMilli(startInMillis), Instant.ofEpochMilli(endInMillis));
    }

    public AbsoluteTimeRange(Instant start) {
        this(start, Instant.MAX);
    }

    public AbsoluteTimeRange() {
        this(Instant.MIN, Instant.MAX);
    }

    public boolean isStartUsed() {
        return !start.equals(Instant.MIN);
    }

    public boolean isEndUsed() {
        return !end.equals(Instant.MAX);
    }

    public static AbsoluteTimeRange justStart(Instant start) {
        return new AbsoluteTimeRange(start, Instant.MAX);
    }

    public static AbsoluteTimeRange justEnd(Instant end) {
        return new AbsoluteTimeRange(Instant.MIN, end);
    }

    @Override
    public RelativeTimeRange toRelativeTimeRange(ProfilingStartEnd profilingStartEnd) {
        Duration relativeStart = isStartUsed()
                ? Duration.between(profilingStartEnd.start(), start)
                : Duration.ZERO;

        Duration relativeEnd = isEndUsed()
                ? Duration.between(profilingStartEnd.start(), end)
                : Duration.between(profilingStartEnd.start(), profilingStartEnd.end());

        return new RelativeTimeRange(relativeStart, relativeEnd);
    }
}
