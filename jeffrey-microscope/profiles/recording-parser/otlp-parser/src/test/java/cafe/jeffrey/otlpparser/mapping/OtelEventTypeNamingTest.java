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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.otlpparser.mapping.OtelEventTypeNaming.OtelEventType;
import cafe.jeffrey.shared.common.model.EventTypeName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtelEventTypeNamingTest {

    @Nested
    class WellKnownTypes {

        @Test
        void asyncProfilerSampleTypes() {
            assertEquals(EventTypeName.OTEL_CPU, OtelEventTypeNaming.resolve("cpu", "nanoseconds").name());
            assertEquals(EventTypeName.OTEL_WALL, OtelEventTypeNaming.resolve("wall", "nanoseconds").name());
            assertEquals(EventTypeName.OTEL_ALLOC, OtelEventTypeNaming.resolve("alloc", "bytes").name());
            assertEquals(EventTypeName.OTEL_LOCK, OtelEventTypeNaming.resolve("lock", "nanoseconds").name());
        }

        @Test
        void ebpfProfilerSampleTypes() {
            assertEquals(EventTypeName.OTEL_SAMPLES, OtelEventTypeNaming.resolve("samples", "count").name());
        }

        @Test
        void pprofHeapAliases() {
            assertEquals(EventTypeName.OTEL_ALLOC, OtelEventTypeNaming.resolve("alloc_space", "bytes").name());
            assertEquals(EventTypeName.OTEL_ALLOC, OtelEventTypeNaming.resolve("allocated_objects", "count").name());
        }

        @Test
        void matchingIsCaseInsensitive() {
            assertEquals(EventTypeName.OTEL_CPU, OtelEventTypeNaming.resolve("CPU", "nanoseconds").name());
        }

        @Test
        void blankTypeFallsBackToSamples() {
            assertEquals(EventTypeName.OTEL_SAMPLES, OtelEventTypeNaming.resolve("", "count").name());
            assertEquals(EventTypeName.OTEL_SAMPLES, OtelEventTypeNaming.resolve(null, null).name());
        }
    }

    @Nested
    class CustomTypes {

        @Test
        void sanitizesUnknownSampleTypes() {
            OtelEventType custom = OtelEventTypeNaming.resolve("off cpu/waits", "nanoseconds");
            assertEquals("otel.off_cpu_waits", custom.name());
            assertTrue(custom.label().contains("off cpu/waits"));
        }

        @Test
        void categoriesAlwaysContainOpenTelemetry() {
            assertTrue(OtelEventTypeNaming.resolve("custom_metric", "count").categories().contains("OpenTelemetry"));
            assertTrue(OtelEventTypeNaming.resolve("cpu", "nanoseconds").categories().contains("OpenTelemetry"));
        }
    }
}
