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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IDGeneratorTest {

    @Nested
    class TimestampOf {

        @Test
        void aGeneratedIdCarriesATime() {
            assertTrue(IDGenerator.timestampOf(IDGenerator.generate()).isPresent());
        }

        @Test
        void readsTheEpochMillisOfACanonicalTimeOrderedId() {
            // 0x01a176cb5700 epoch millis is 2026-10-26T06:00:00Z
            assertEquals(Optional.of(Instant.parse("2026-10-26T06:00:00Z")),
                    IDGenerator.timestampOf("01a176cb-5700-7abc-9f01-2b3c4d5e6f70"));
        }

        @Test
        void idsOfOneProcessOrderAsTheirStrings() {
            String first = IDGenerator.generate();
            String second = IDGenerator.generate();

            assertTrue(first.compareTo(second) < 0, first + " before " + second);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "s-1",
                "",
                // version 4: random, no time in it
                "3f2b8c1e-9d4a-4f6b-8e2c-1a2b3c4d5e6f",
                // not canonical: upper case, and short fields fromString would accept
                "01A176CB-5700-7ABC-9F01-2B3C4D5E6F70",
                "1a176cb-5700-7abc-9f01-2b3c4d5e6f70",
                // version 7 with a non-standard variant
                "01a176cb-5700-7abc-cf01-2b3c4d5e6f70"
        })
        void anythingElseCarriesNoTime(String id) {
            assertEquals(Optional.empty(), IDGenerator.timestampOf(id));
        }

        @Test
        void noIdCarriesNoTime() {
            assertEquals(Optional.empty(), IDGenerator.timestampOf(null));
        }
    }
}
