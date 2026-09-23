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

package cafe.jeffrey.jfr.events.servlet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sink's whole job is to guarantee, on the way in, what Jeffrey's attribute index requires on
 * the way out — so every case here is one of that index's silent drops, turned into something the
 * writer finds out about.
 */
class HttpExchangeAttributesTest {

    @Nested
    @DisplayName("What is recorded")
    class Recording {

        @Test
        @DisplayName("nothing attached is empty, and stays distinguishable from an empty object")
        void nothingAttached() {
            HttpExchangeAttributes attributes = new HttpExchangeAttributes();

            assertTrue(attributes.isEmpty());
            assertEquals("{}", attributes.json());
        }

        @Test
        @DisplayName("scalars keep their JSON type, so the index can compare them as numbers")
        void scalarsKeepTheirType() {
            HttpExchangeAttributes attributes = new HttpExchangeAttributes()
                    .put("tenant", "acme")
                    .put("retries", 2)
                    .put("cached", true);

            assertFalse(attributes.isEmpty());
            assertEquals("{\"tenant\":\"acme\",\"retries\":2,\"cached\":true}", attributes.json());
        }

        @Test
        @DisplayName("a long value is recorded whole, because a shortened one could never be matched")
        void longValuesAreRecordedWhole() {
            String value = "x".repeat(1000);

            HttpExchangeAttributes attributes = new HttpExchangeAttributes().put("blob", value);

            assertEquals("{\"blob\":\"" + value + "\"}", attributes.json());
        }
    }

    @Nested
    @DisplayName("What the attribute index would have dropped")
    class Refused {

        @Test
        @DisplayName("a null or blank value records nothing, because the index drops it")
        void emptyValuesRecordNothing() {
            HttpExchangeAttributes attributes = new HttpExchangeAttributes()
                    .put("absent", null)
                    .put("empty", "")
                    .put("blank", "   ");

            assertTrue(attributes.isEmpty());
        }

        @Test
        @DisplayName("a repeated key keeps the first value, because a repeat would become two rows")
        void repeatedKeyKeepsTheFirst() {
            HttpExchangeAttributes attributes = new HttpExchangeAttributes()
                    .put("tenant", "acme")
                    .put("tenant", "globex");

            assertEquals("{\"tenant\":\"acme\"}", attributes.json());
        }

        @Test
        @DisplayName("a key the index could not address is refused out loud")
        void unaddressableKeysThrow() {
            HttpExchangeAttributes attributes = new HttpExchangeAttributes();

            assertThrows(IllegalArgumentException.class, () -> attributes.put(null, "acme"));
            assertThrows(IllegalArgumentException.class, () -> attributes.put("  ", "acme"));

            IllegalArgumentException quoted = assertThrows(IllegalArgumentException.class, () ->
                    attributes.put("ten\"ant", "acme"));
            assertTrue(quoted.getMessage().contains("quote"), quoted.getMessage());
        }
    }
}
