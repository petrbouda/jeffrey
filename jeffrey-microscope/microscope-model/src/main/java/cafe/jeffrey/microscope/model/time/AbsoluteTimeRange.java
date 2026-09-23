/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.model.time;

import cafe.jeffrey.microscope.model.ProfilingStartEnd;

import java.time.Duration;
import java.time.Instant;

public record AbsoluteTimeRange(Instant start, Instant end) implements TimeRange {

    public AbsoluteTimeRange(long startInMillis, long endInMillis) {
        this(Instant.ofEpochMilli(startInMillis), Instant.ofEpochMilli(endInMillis));
    }


    public boolean isStartUsed() {
        return !start.equals(Instant.MIN);
    }

    public boolean isEndUsed() {
        return !end.equals(Instant.MAX);
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
