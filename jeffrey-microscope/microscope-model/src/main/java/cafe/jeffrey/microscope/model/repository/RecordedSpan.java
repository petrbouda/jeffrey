/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.model.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * How far a session's recordings reach, from nothing but when each one started — the timestamp
 * the profiler puts in its name.
 *
 * <p>The profiler's chunk length is its own configuration, which never reaches the volume, so it
 * is measured: the lower median of the spacing between consecutive starts. Within one session the
 * chunks are equal, and a median stays put when one chunk is missing, the last one is cut short,
 * or the profiler started a moment late.
 */
public final class RecordedSpan {

    private static final int FEWEST_STARTS_TO_MEASURE = 2;

    private RecordedSpan() {
    }

    /**
     * The length of one chunk, measured from the spacing of their starts; empty while fewer than
     * two chunks give it a spacing.
     */
    public static Optional<Duration> chunkLength(List<Instant> starts) {
        if (starts.size() < FEWEST_STARTS_TO_MEASURE) {
            return Optional.empty();
        }
        List<Instant> oldestFirst = starts.stream().sorted().toList();
        List<Long> spacings = new ArrayList<>();
        for (int i = 1; i < oldestFirst.size(); i++) {
            spacings.add(Duration.between(oldestFirst.get(i - 1), oldestFirst.get(i)).toMillis());
        }
        spacings.sort(Comparator.naturalOrder());
        return Optional.of(Duration.ofMillis(spacings.get((spacings.size() - 1) / 2)));
    }

    /**
     * When the newest recording's data ends: its start plus one chunk, or its start alone while the
     * chunk length cannot be measured; empty without a recording.
     */
    public static Optional<Instant> end(List<Instant> starts) {
        Optional<Instant> newest = starts.stream().max(Comparator.naturalOrder());
        return newest.map(start -> chunkLength(starts).map(start::plus).orElse(start));
    }
}
