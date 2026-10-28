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

package cafe.jeffrey.shared.common;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The UTC offset a recording's name carries: async-profiler writes {@code %t} in local time, so
 * the provisioner writes the offset right after it, and every reader reads the name in it.
 */
class JeffreyLayoutTest {

    @Nested
    class RecordingTemplate {

        @Test
        void carriesTheOffsetRightAfterTheTimestamp() {
            assertEquals("profile-%t+0100.jfr",
                    JeffreyLayout.recordingTemplate("profile-%t.jfr", ZoneOffset.ofHours(1)));
            assertEquals("/data/session/profile-%t+0000.jfr",
                    JeffreyLayout.recordingTemplate("/data/session/profile-%t.jfr", ZoneOffset.UTC));
        }

        @Test
        void refusesATemplateWithoutATimestampBeforeTheExtension() {
            assertThrows(IllegalArgumentException.class,
                    () -> JeffreyLayout.recordingTemplate("profile.jfr", ZoneOffset.UTC));
        }
    }

    /** A file share refuses {@code :} in a name, so an offset is written without it. */
    @Nested
    class OffsetSegment {

        @Test
        void writesUtcAsZeros() {
            assertEquals("+0000", JeffreyLayout.offsetSegment(ZoneOffset.UTC));
        }

        @Test
        void writesHoursAndMinutesWithoutAColon() {
            assertEquals("+0200", JeffreyLayout.offsetSegment(ZoneOffset.ofHours(2)));
            assertEquals("+0530", JeffreyLayout.offsetSegment(ZoneOffset.ofHoursMinutes(5, 30)));
            assertEquals("-0330", JeffreyLayout.offsetSegment(ZoneOffset.ofHoursMinutes(-3, -30)));
        }
    }
}
