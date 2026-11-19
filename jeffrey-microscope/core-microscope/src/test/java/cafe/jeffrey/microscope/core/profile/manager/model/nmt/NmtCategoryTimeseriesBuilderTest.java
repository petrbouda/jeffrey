/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.core.profile.manager.model.nmt;

import cafe.jeffrey.microscope.core.profile.jfr.JfrThreadImpl;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.core.profile.persistence.api.GenericRecord;
import cafe.jeffrey.microscope.core.profile.timeseries.SingleSerie;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NmtCategoryTimeseriesBuilderTest {

    private static final Instant RECORDING_START = Instant.parse("2026-03-01T10:00:00Z");
    /** Seconds 0 to 2 of the recording. */
    private static final RelativeTimeRange THREE_SECONDS = new RelativeTimeRange(0, 2_000);

    private static GenericRecord usage(long millis, String category, long committed) {
        ObjectNode fields = JsonNodeFactory.instance.objectNode();
        if (category != null) {
            fields.put("type", category);
        }
        fields.put("committed", committed);
        Duration fromStart = Duration.ofMillis(millis);
        return new GenericRecord(
                Type.NATIVE_MEMORY_USAGE,
                null,
                RECORDING_START.plus(fromStart),
                fromStart,
                Duration.ZERO,
                new JfrThreadImpl(1, 1, "JFR Periodic Tasks", false),
                null,
                1,
                0,
                fields);
    }

    private static List<SingleSerie> build(List<GenericRecord> records) {
        NmtCategoryTimeseriesBuilder builder = new NmtCategoryTimeseriesBuilder(THREE_SECONDS);
        records.forEach(builder::onRecord);
        return builder.build().series();
    }

    private static List<List<Long>> points(long first, long second, long third) {
        return List.of(List.of(0L, first), List.of(1L, second), List.of(2L, third));
    }

    @Test
    void drawsEachCategoryAsItsLargestCommittedPerSecondCarriedForward() {
        List<SingleSerie> series = build(List.of(
                usage(1_100, "Heap", 300),
                usage(1_600, "Heap", 500),
                usage(1_900, "Heap", 400)));

        assertEquals(1, series.size());
        assertEquals("Heap", series.getFirst().name());
        assertEquals(points(0, 500, 500), series.getFirst().data());
    }

    /** Ten categories: the eight with the highest peak are drawn, largest first; the two smallest are one "Other" band. */
    @Test
    void keepsTheEightLargestCategoriesAndSumsTheRestIntoOther() {
        List<GenericRecord> records = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> usage(1_000, "c" + i, i * 100L))
                .toList();

        List<SingleSerie> series = build(records);

        assertEquals(List.of("c10", "c9", "c8", "c7", "c6", "c5", "c4", "c3", "Other"),
                series.stream().map(SingleSerie::name).toList());
        assertEquals(points(0, 300, 300), series.get(8).data());
    }

    @Test
    void namesAnUntypedRecordUnknownAndIgnoresANegativeCommitted() {
        List<SingleSerie> series = build(List.of(
                usage(1_000, null, 200),
                usage(2_000, "Code", -1)));

        assertEquals(List.of("Unknown"), series.stream().map(SingleSerie::name).toList());
        assertEquals(points(0, 200, 200), series.getFirst().data());
    }
}
