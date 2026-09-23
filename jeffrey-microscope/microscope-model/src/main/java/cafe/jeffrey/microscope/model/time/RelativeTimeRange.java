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

/**
 * @param start start from the beginning of the recording.
 * @param end   end from the beginning of the recording.
 */
public record RelativeTimeRange(Duration start, Duration end) implements TimeRange {

    public RelativeTimeRange(long startInMillis, long endInMillis) {
        this(Duration.ofMillis(startInMillis), Duration.ofMillis(endInMillis));
    }

    public RelativeTimeRange(ProfilingStartEnd profilingStartEnd) {
        this(Duration.ZERO, Duration.between(profilingStartEnd.start(), profilingStartEnd.end()));
    }

    public Duration duration() {
        return end.minus(start);
    }

    @Override
    public RelativeTimeRange toRelativeTimeRange(ProfilingStartEnd profilingStartEnd) {
        return this;
    }
}
