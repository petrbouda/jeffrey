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

package pbouda.jeffrey.profile.manager.builder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.profile.common.event.*;
import pbouda.jeffrey.profile.manager.model.gc.configuration.GCConfigurationData;
import pbouda.jeffrey.provider.profile.model.GenericRecord;
import pbouda.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GCConfigurationEventBuilder")
class GCConfigurationEventBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode createGcConfigurationFields() {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("youngCollector", "G1New");
        fields.put("oldCollector", "G1Old");
        fields.put("isExplicitGCConcurrent", true);
        fields.put("isExplicitGCDisabled", false);
        fields.put("pauseTarget", 200000000L);
        fields.put("parallelGCThreads", 8);
        fields.put("concurrentGCThreads", 2);
        fields.put("usesDynamicGCThreads", true);
        return fields;
    }

    private static ObjectNode createGcHeapConfigurationFields() {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("minSize", 8388608L);
        fields.put("maxSize", 4294967296L);
        fields.put("initialSize", 268435456L);
        fields.put("usesCompressedOops", true);
        fields.put("compressedOopsMode", "Zero based");
        fields.put("objectAlignment", 8);
        fields.put("heapAddressBits", 32);
        return fields;
    }

    private static ObjectNode createGcTlabConfigurationFields() {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("usesTLABs", true);
        fields.put("minTLABSize", 2048L);
        fields.put("tlabRefillWasteLimit", 64L);
        return fields;
    }

    private static ObjectNode createGcSurvivorConfigurationFields() {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("maxTenuringThreshold", 15);
        fields.put("initialTenuringThreshold", 15);
        return fields;
    }

    private static ObjectNode createYoungGenerationConfigurationFields() {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("maxSize", 2576980377L);
        fields.put("minSize", 1363148L);
        fields.put("newRatio", 2);
        return fields;
    }

    private static GenericRecord createRecord(Type type, ObjectNode fields) {
        return new GenericRecord(
                type,
                type.code(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Duration.ZERO,
                Duration.ZERO,
                null,
                null,
                1,
                0,
                fields);
    }

    @Nested
    @DisplayName("AllConfigEventsPresent")
    class AllConfigEventsPresent {

        @Test
        @DisplayName("All five GC configuration event types are correctly deserialized")
        void allGcConfigurationEventsAreDeserialized() {
            GCConfigurationEventBuilder builder = new GCConfigurationEventBuilder();

            builder.onRecord(createRecord(Type.GC_CONFIGURATION, createGcConfigurationFields()));
            builder.onRecord(createRecord(Type.GC_HEAP_CONFIGURATION, createGcHeapConfigurationFields()));
            builder.onRecord(createRecord(Type.GC_TLAB_CONFIGURATION, createGcTlabConfigurationFields()));
            builder.onRecord(createRecord(Type.GC_SURVIVOR_CONFIGURATION, createGcSurvivorConfigurationFields()));
            builder.onRecord(createRecord(Type.YOUNG_GENERATION_CONFIGURATION, createYoungGenerationConfigurationFields()));

            GCConfigurationData data = builder.build();

            assertNotNull(data);
            assertEquals(GarbageCollectorType.G1, data.detectedType());

            // GCConfiguration
            GCConfiguration collector = data.collector();
            assertNotNull(collector);
            assertEquals("G1New", collector.youngCollector());
            assertEquals("G1Old", collector.oldCollector());
            assertTrue(collector.isExplicitGCConcurrent());
            assertFalse(collector.isExplicitGCDisabled());
            assertEquals(200000000L, collector.pauseTarget());

            // GCThreadConfiguration (extracted from GC_CONFIGURATION event)
            GCThreadConfiguration threads = data.threads();
            assertNotNull(threads);
            assertEquals(8, threads.parallelGCThreads());
            assertEquals(2, threads.concurrentGCThreads());
            assertTrue(threads.usesDynamicGCThreads());

            // GCHeapConfiguration
            GCHeapConfiguration heap = data.heap();
            assertNotNull(heap);
            assertEquals(8388608L, heap.minSize());
            assertEquals(4294967296L, heap.maxSize());
            assertEquals(268435456L, heap.initialSize());
            assertTrue(heap.usesCompressedOops());
            assertEquals("Zero based", heap.compressedOopsMode());
            assertEquals(8, heap.objectAlignment());
            assertEquals(32, heap.heapAddressBits());

            // GCTLABConfiguration
            GCTLABConfiguration tlab = data.tlab();
            assertNotNull(tlab);
            assertTrue(tlab.usesTLABs());
            assertEquals(2048L, tlab.minTLABSize());
            assertEquals(64L, tlab.tlabRefillWasteLimit());

            // GCSurvivorConfiguration
            GCSurvivorConfiguration survivor = data.survivor();
            assertNotNull(survivor);
            assertEquals(15, survivor.maxTenuringThreshold());
            assertEquals(15, survivor.initialTenuringThreshold());

            // GCYoungGenerationConfiguration
            GCYoungGenerationConfiguration youngGen = data.youngGeneration();
            assertNotNull(youngGen);
            assertEquals(2576980377L, youngGen.maxSize());
            assertEquals(1363148L, youngGen.minSize());
            assertEquals(2, youngGen.newRatio());
        }
    }

    @Nested
    @DisplayName("MissingIndividualEvents")
    class MissingIndividualEvents {

        @Test
        @DisplayName("Only GC_CONFIGURATION event yields null heap, survivor, tlab and youngGeneration")
        void onlyGcConfigurationEventYieldsNullForOtherSections() {
            GCConfigurationEventBuilder builder = new GCConfigurationEventBuilder();

            builder.onRecord(createRecord(Type.GC_CONFIGURATION, createGcConfigurationFields()));

            GCConfigurationData data = builder.build();

            assertNotNull(data);
            assertEquals(GarbageCollectorType.G1, data.detectedType());

            assertNotNull(data.collector());
            assertNotNull(data.threads());

            assertNull(data.heap());
            assertNull(data.survivor());
            assertNull(data.tlab());
            assertNull(data.youngGeneration());
        }
    }

    @Nested
    @DisplayName("NullGcConfigurationCausesNPE")
    class NullGcConfigurationCausesNPE {

        @Test
        @DisplayName("Building without GC_CONFIGURATION event throws NullPointerException")
        void buildWithoutGcConfigurationThrowsNpe() {
            GCConfigurationEventBuilder builder = new GCConfigurationEventBuilder();

            assertThrows(NullPointerException.class, builder::build);
        }
    }
}
