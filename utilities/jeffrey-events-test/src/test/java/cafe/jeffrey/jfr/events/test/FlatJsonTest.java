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

package cafe.jeffrey.jfr.events.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The attribute reader is held to the same shape Jeffrey's own attribute index accepts: a flat
 * object of scalars, values as text.
 */
class FlatJsonTest {

    @Test
    @DisplayName("nothing recorded reads as no pairs")
    void absentIsEmpty() {
        assertEquals(Map.of(), FlatJson.parse(null));
        assertEquals(Map.of(), FlatJson.parse("  "));
        assertEquals(Map.of(), FlatJson.parse("{}"));
    }

    @Test
    @DisplayName("scalars of every kind come back as text, in the order they were written")
    void scalarsAreText() {
        Map<String, String> pairs =
                FlatJson.parse("{\"tenant\":\"acme\",\"retries\":2,\"ratio\":0.5,\"cached\":true,\"gone\":null}");

        assertEquals(List.of("tenant", "retries", "ratio", "cached", "gone"), List.copyOf(pairs.keySet()));
        assertEquals("acme", pairs.get("tenant"));
        assertEquals("2", pairs.get("retries"));
        assertEquals("0.5", pairs.get("ratio"));
        assertEquals("true", pairs.get("cached"));
        assertEquals("null", pairs.get("gone"));
    }

    @Test
    @DisplayName("escapes are undone, in keys as well as values")
    void escapesAreUndone() {
        Map<String, String> pairs = FlatJson.parse("{\"sql\":\"SELECT \\\"name\\\"\\n\\tFROM t\",\"nul\":\"\\u0001\"}");

        assertEquals("SELECT \"name\"\n\tFROM t", pairs.get("sql"));
        assertEquals("\u0001", pairs.get("nul"));
    }

    @Test
    @DisplayName("whitespace between the pairs is not part of them")
    void whitespaceIsSkipped() {
        assertEquals(Map.of("a", "1", "b", "x"), FlatJson.parse("{ \"a\" : 1 , \"b\" : \"x\" }"));
    }

    @Test
    @DisplayName("a nested object is refused, because the index would have dropped it")
    void nestedValuesAreRefused() {
        IllegalArgumentException nested = assertThrows(IllegalArgumentException.class, () ->
                FlatJson.parse("{\"headers\":{\"x-tenant-id\":\"acme\"}}"));
        assertTrue(nested.getMessage().contains("flat map of scalars"), nested.getMessage());

        assertThrows(IllegalArgumentException.class, () -> FlatJson.parse("{\"values\":[1,2]}"));
    }

    @Test
    @DisplayName("text that is not an object at all is refused rather than half-read")
    void malformedIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> FlatJson.parse("not json"));
        assertThrows(IllegalArgumentException.class, () -> FlatJson.parse("{\"a\":1"));
        assertThrows(IllegalArgumentException.class, () -> FlatJson.parse("{\"a\"}"));
    }
}
