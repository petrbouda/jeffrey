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

package cafe.jeffrey.jfr.events.trace;

import cafe.jeffrey.jfr.events.notification.NotificationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventAttributesTest {

    @Test
    @DisplayName("values of every supported type render as one JSON object, in put order")
    void rendersAllTypes() {
        String json = EventAttributes.create()
                .put("cache", "miss")
                .put("retries", 2L)
                .put("ratio", 0.5)
                .put("fallback", true)
                .json();

        assertEquals("{\"cache\":\"miss\",\"retries\":2,\"ratio\":0.5,\"fallback\":true}", json);
    }

    @Test
    @DisplayName("nothing put renders as an empty object")
    void emptyRendersAsEmptyObject() {
        assertEquals("{}", EventAttributes.create().json());
    }

    @Test
    @DisplayName("quotes, backslashes and control characters are escaped, so any string is safe")
    void escapesStrings() {
        String json = EventAttributes.create()
                .put("sql", "SELECT \"name\" FROM t\nWHERE path = 'C:\\tmp'")
                .put("nul", "\u0001")
                .json();

        assertEquals(
                "{\"sql\":\"SELECT \\\"name\\\" FROM t\\nWHERE path = 'C:\\\\tmp'\",\"nul\":\"\\u0001\"}",
                json);
    }

    @Test
    @DisplayName("a null string value is recorded as JSON null, a null key is refused")
    void nullHandling() {
        assertEquals("{\"missing\":null}", EventAttributes.create().put("missing", (String) null).json());
        assertThrows(NullPointerException.class, () -> EventAttributes.create().put(null, "value"));
    }

    @Test
    @DisplayName("non-finite numbers, which JSON cannot encode, are recorded as null")
    void nonFiniteNumbersAreNull() {
        String json = EventAttributes.create()
                .put("nan", Double.NaN)
                .put("inf", Double.POSITIVE_INFINITY)
                .json();

        assertEquals("{\"nan\":null,\"inf\":null}", json);
    }

    @Test
    @DisplayName("the rendered object lands on the event's attributes field as-is")
    void fillsTheAttributesField() {
        TraceSpanEvent event = new TraceSpanEvent();

        event.attributes = EventAttributes.create().put("chunk", 42L).json();

        assertEquals("{\"chunk\":42}", event.attributes);
    }

    /**
     * The same builder fills either family's field. It matters that the encoding is identical rather
     * than merely similar: one derivation flattens both, and one renderer draws both.
     */
    @Test
    @DisplayName("it fills an instant's attributes field the same way")
    void fillsTheInstantAttributesField() {
        NotificationEvent notification = new NotificationEvent();

        notification.attributes = EventAttributes.create().put("chunk", 42L).json();

        assertEquals("{\"chunk\":42}", notification.attributes);
    }
}
