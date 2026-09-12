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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.StreamingEvent;
import cafe.jeffrey.hub.api.v1.TypedValue;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubReplayCollectorTest {
    private static final HubSessionRef REF = new HubSessionRef("hub", "workspace", "project", "session");

    @Test
    void rowLimitCancelsEvenWhenHandleArrivesAfterBatchAndIgnoresLateCallbacks() {
        HubReplayCollector collector = new HubReplayCollector(REF, 1, 4096);
        collector.acknowledge("workspace", "project");
        collector.accept(batch("first", "second"));
        AtomicInteger cancels = new AtomicInteger();
        collector.cancellation(cancels::incrementAndGet);
        collector.stop("cancelled");
        collector.accept(batch("late"));
        collector.completed(0);
        assertEquals(1, cancels.get());
        assertEquals(1, collector.result().path("events").size());
        assertEquals("row_limit", collector.result().path("termination").asText());
        assertFalse(collector.result().path("complete").asBoolean());
    }

    @Test
    void unacknowledgedLegacyHubCannotReturnUnscopedRows() {
        HubReplayCollector collector = new HubReplayCollector(REF, 10, 4096);
        collector.accept(batch("wrong project"));
        collector.completed(0);
        assertTrue(collector.result().path("events").isEmpty());
        assertEquals("unsupported_hub", collector.result().path("termination").asText());
    }

    @Test
    void byteLimitRetainsWholeEventsAndCountsUtf8() {
        HubReplayCollector collector = new HubReplayCollector(REF, 10, 4096);
        collector.acknowledge("workspace", "project");
        collector.accept(batch("small", "猫".repeat(2000)));
        assertEquals(1, collector.result().path("events").size());
        assertEquals("byte_limit", collector.result().path("termination").asText());
        assertTrue(Json.toString(collector.result()).getBytes(StandardCharsets.UTF_8).length <= 4096);
    }

    @Test
    void sourceErrorsPreventCompleteEvenAfterNormalCompletion() {
        HubReplayCollector collector = new HubReplayCollector(REF, 10, 4096);
        collector.acknowledge("workspace", "project");
        collector.completed(2);
        assertFalse(collector.result().path("complete").asBoolean());
        assertEquals(2, collector.result().path("sourceErrors").asLong());
        assertEquals("source_errors", collector.result().path("termination").asText());
    }

    @Test
    void acknowledgedEmptyReplayWithSummaryIsComplete() {
        HubReplayCollector collector = new HubReplayCollector(REF, 10, 4096);
        collector.acknowledge("workspace", "project");
        collector.completed(0);
        assertTrue(collector.result().path("complete").asBoolean());
    }

    @Test
    void wrongScopeAcknowledgementCannotReleaseRows() {
        HubReplayCollector collector = new HubReplayCollector(REF, 10, 4096);
        collector.acknowledge("workspace", "other-project");
        collector.accept(batch("wrong project"));
        assertTrue(collector.result().path("events").isEmpty());
        assertEquals("scope_mismatch", collector.result().path("termination").asText());
    }

    @Test
    void acknowledgementWithoutTerminalSummaryLeavesCoverageUnknown() {
        HubReplayCollector collector = new HubReplayCollector(REF, 10, 4096);
        collector.acknowledge("workspace", "project");
        collector.streamCompleted();
        assertEquals("missing_coverage", collector.result().path("termination").asText());
        assertFalse(collector.result().path("coverageKnown").asBoolean());
        assertEquals(Json.toString(collector.result()).getBytes(StandardCharsets.UTF_8).length,
                collector.result().path("resultBytes").asInt());
    }

    private static EventBatch batch(String... messages) {
        EventBatch.Builder batch = EventBatch.newBuilder();
        for (String message : messages) {
            batch.addEvents(StreamingEvent.newBuilder().setSessionId("session").setEventType("jdk.JavaExceptionThrow")
                    .setTimestamp(1).putFields("message", TypedValue.newBuilder().setStringValue(message).build()));
        }
        return batch.build();
    }
}
