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

package pbouda.jeffrey.basics;

import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.common.ProfilingStartEnd;

import java.time.Instant;
import java.util.function.Supplier;

public class StartEndTimeCollector implements Collector<ProfilingStartEnd, ProfilingStartEnd> {
    @Override
    public Supplier<ProfilingStartEnd> empty() {
        return () -> new ProfilingStartEnd(null, null);
    }

    @Override
    public ProfilingStartEnd combiner(ProfilingStartEnd partial1, ProfilingStartEnd partial2) {
        Instant start = resolveRecordingStart(partial1.start(), partial2.start());
        Instant end = resolveLatestEvent(partial1.end(), partial2.end());
        return new ProfilingStartEnd(start, end);
    }

    private static Instant resolveRecordingStart(Instant start1, Instant start2) {
        if (start1 == null) {
            return start2;
        } else if (start2 == null) {
            return start1;
        } else {
            return start1.isBefore(start2) ? start1 : start2;
        }
    }

    private static Instant resolveLatestEvent(Instant end1, Instant end2) {
        if (end1 == null) {
            return end2;
        } else if (end2 == null) {
            return end1;
        } else {
            return end1.isAfter(end2) ? end1 : end2;
        }
    }

    @Override
    public ProfilingStartEnd finisher(ProfilingStartEnd combined) {
        return combined;
    }
}
