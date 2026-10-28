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

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The zone a recording's name carries: async-profiler writes {@code %t} in local time, so the
 * provisioner writes the zone beside it, and every reader turns the name back into that zone.
 */
class JeffreyLayoutTest {

    @Nested
    class RecordingTemplate {

        @Test
        void carriesTheZoneAfterTheTimestamp() {
            assertEquals("profile-%t.Europe~Prague.jfr",
                    JeffreyLayout.recordingTemplate("profile-%t.jfr", ZoneId.of("Europe/Prague")));
            assertEquals("/data/session/profile-%t.UTC.jfr",
                    JeffreyLayout.recordingTemplate("/data/session/profile-%t.jfr", ZoneOffset.UTC));
        }

        @Test
        void refusesATemplateWithoutATimestampBeforeTheExtension() {
            assertThrows(IllegalArgumentException.class,
                    () -> JeffreyLayout.recordingTemplate("profile.jfr", ZoneOffset.UTC));
        }
    }

    @Nested
    class ZoneSegment {

        @Test
        void roundTripsARegion() {
            ZoneId zone = ZoneId.of("America/Argentina/Buenos_Aires");

            assertEquals("America~Argentina~Buenos_Aires", JeffreyLayout.zoneSegment(zone));
            assertEquals(Optional.of(zone), JeffreyLayout.zoneOfSegment(JeffreyLayout.zoneSegment(zone)));
        }

        @Test
        void writesUtcByName() {
            assertEquals("UTC", JeffreyLayout.zoneSegment(ZoneOffset.UTC));
            assertEquals("UTC", JeffreyLayout.zoneSegment(ZoneId.of("Etc/UTC")));
            assertEquals(Optional.of(ZoneId.of("UTC")), JeffreyLayout.zoneOfSegment("UTC"));
        }

        /** A file share refuses {@code :} in a name, so a fixed offset is written without it. */
        @Test
        void writesAFixedOffsetWithoutAColon() {
            assertEquals("+0530", JeffreyLayout.zoneSegment(ZoneOffset.ofHoursMinutes(5, 30)));
            assertEquals(Optional.of(ZoneOffset.ofHoursMinutes(5, 30)), JeffreyLayout.zoneOfSegment("+0530"));
        }

        @Test
        void aSegmentNamingNoZoneIsNone() {
            assertEquals(Optional.empty(), JeffreyLayout.zoneOfSegment("Nowhere~Atlantis"));
        }
    }
}
