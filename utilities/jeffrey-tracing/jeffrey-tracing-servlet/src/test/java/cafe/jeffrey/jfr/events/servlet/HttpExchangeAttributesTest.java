/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

    private static final int MAX_VALUE_LENGTH = 256;

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
        @DisplayName("an oversized value is truncated and marked")
        void longValuesAreTruncated() {
            String value = "x".repeat(MAX_VALUE_LENGTH + 100);

            HttpExchangeAttributes attributes = new HttpExchangeAttributes().put("blob", value);

            assertEquals("{\"blob\":\"" + "x".repeat(MAX_VALUE_LENGTH) + "…\"}", attributes.json());
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
