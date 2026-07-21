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

package cafe.jeffrey.otlpparser.mapping;

import cafe.jeffrey.otlpparser.mapping.OtelEventTypeNaming.OtelEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OtelEventTypeNamingTest {

    @Test
    void codeAndLabelAreTheRawSampleTypeVerbatim() {
        assertEquals("cpu", OtelEventTypeNaming.resolve("cpu").name());
        assertEquals("alloc", OtelEventTypeNaming.resolve("alloc").name());
        assertEquals("alloc_space", OtelEventTypeNaming.resolve("alloc_space").name());

        OtelEventType resolved = OtelEventTypeNaming.resolve("alloc");
        assertEquals("alloc", resolved.label());
    }

    @Test
    void blankTypeFallsBackToSamples() {
        assertEquals("samples", OtelEventTypeNaming.resolve("").name());
        assertEquals("samples", OtelEventTypeNaming.resolve(null).name());
    }
}
