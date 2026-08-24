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

package cafe.jeffrey.profile.parser.fields;

import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PooledFieldExtractorTest {

    private static final String LONG_TEXT = "x".repeat(64);
    private static final String LONGER_TEXT = "y".repeat(200);
    private static final String SHORT_TEXT = "short";

    @Nested
    class NothingQualifies {

        @Test
        void leavesShortStringsInline() {
            ObjectNode fields = Json.createObject();
            fields.put("uri", SHORT_TEXT);
            fields.put("statusCode", 200);

            assertNull(PooledFieldExtractor.extractLargest(fields));
            assertEquals(SHORT_TEXT, fields.get("uri").asString());
        }

        @Test
        void ignoresNonStringValuesWhateverTheirSize() {
            ObjectNode fields = Json.createObject();
            fields.put("bigNumber", Long.MAX_VALUE);
            fields.put("flag", true);
            fields.putNull("absent");

            assertNull(PooledFieldExtractor.extractLargest(fields));
        }

        @Test
        void leavesTextJustBelowTheThreshold() {
            ObjectNode fields = Json.createObject();
            fields.put("message", "z".repeat(63));

            assertNull(PooledFieldExtractor.extractLargest(fields));
            assertTrue(fields.has("message"));
        }
    }

    @Nested
    class SomethingQualifies {

        @Test
        void extractsTheOnlyQualifyingFieldAndRemovesIt() {
            ObjectNode fields = Json.createObject();
            fields.put("sql", LONG_TEXT);
            fields.put("rows", 5);

            PooledFieldExtractor.PooledValue pooled = PooledFieldExtractor.extractLargest(fields);

            assertEquals("sql", pooled.field());
            assertEquals(LONG_TEXT, pooled.text());
            assertFalse(fields.has("sql"));
            assertEquals(5, fields.get("rows").asInt());
        }

        @Test
        void picksTheLargestWhenSeveralQualify() {
            ObjectNode fields = Json.createObject();
            fields.put("params", LONG_TEXT);
            fields.put("sql", LONGER_TEXT);

            PooledFieldExtractor.PooledValue pooled = PooledFieldExtractor.extractLargest(fields);

            assertEquals("sql", pooled.field());
            assertEquals(LONGER_TEXT, pooled.text());
            // The runner-up stays inline: one field per event is the whole contract.
            assertEquals(LONG_TEXT, fields.get("params").asString());
        }

        @Test
        void extractsExactlyAtTheThreshold() {
            ObjectNode fields = Json.createObject();
            fields.put("result", LONG_TEXT);

            PooledFieldExtractor.PooledValue pooled = PooledFieldExtractor.extractLargest(fields);

            assertEquals("result", pooled.field());
        }
    }
}
