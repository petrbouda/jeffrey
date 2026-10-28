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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordedSpanTest {

    private static final Instant T0 = Instant.parse("2026-09-14T10:00:00Z");

    private static Instant minutes(long minutes) {
        return T0.plusSeconds(minutes * 60);
    }

    @Test
    void theChunkLengthIsTheLowerMedianSpacingWhateverTheOrder() {
        // A missing chunk (20 to 40) and a late one (55) leave the median where the chunks are
        assertEquals(Optional.of(Duration.ofMinutes(10)),
                RecordedSpan.chunkLength(List.of(minutes(40), minutes(0), minutes(10), minutes(20), minutes(50))));
    }

    @Test
    void aSingleStartGivesNoLength() {
        assertEquals(Optional.empty(), RecordedSpan.chunkLength(List.of(minutes(0))));
    }

    @Test
    void theDataEndsOneChunkPastTheNewestStart() {
        assertEquals(Optional.of(minutes(45)), RecordedSpan.end(List.of(minutes(0), minutes(15), minutes(30))));
    }

    @Test
    void oneChunkEndsAtItsStartAndNoneHasNoEnd() {
        assertEquals(Optional.of(minutes(5)), RecordedSpan.end(List.of(minutes(5))));
        assertEquals(Optional.empty(), RecordedSpan.end(List.of()));
    }
}
