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

package cafe.jeffrey.agent;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process proof that {@code jeffrey.AppInformation} is emitted once at the
 * start of every JFR chunk (the {@code @Period("beginChunk")} behaviour) and
 * that the identity fields round-trip through the JFR binary format.
 */
class AppInformationEmitterTest {

    private static final String EVENT_NAME = "jeffrey.AppInformation";

    private static final AppInformation IDENTITY = new AppInformation(
            "$default",
            "11111111-2222-3333-4444-555555555555",
            "payments-api",
            "Payments API (prod)",
            "pod-7c9",
            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            2,
            "cluster=eu;namespace=prod",
            1700000000000L);

    @Test
    void emittedInEveryChunkWithExpectedFields() throws Exception {
        AppInformationEmitter emitter = new AppInformationEmitter(IDENTITY);
        FlightRecorder.addPeriodicEvent(AppInformationEvent.class, emitter);

        Path file = Files.createTempFile("appinfo-roundtrip", ".jfr");
        try (Recording recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();
            // Starting and stopping a second recording forces a chunk rotation
            // in the active repository, so the dump spans two chunks.
            try (Recording rotation = new Recording()) {
                rotation.start();
                rotation.stop();
            }
            recording.stop();
            recording.dump(file);

            List<RecordedEvent> events = readEvents(file);

            // One per chunk → at least two across the rotation above.
            assertTrue(events.size() >= 2,
                    "expected jeffrey.AppInformation in every chunk, found " + events.size());

            RecordedEvent event = events.getFirst();
            assertEquals("$default", event.getString("workspaceId"));
            assertEquals("11111111-2222-3333-4444-555555555555", event.getString("projectId"));
            assertEquals("payments-api", event.getString("projectName"));
            assertEquals("Payments API (prod)", event.getString("projectLabel"));
            assertEquals("pod-7c9", event.getString("instanceId"));
            assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", event.getString("sessionId"));
            assertEquals(2, event.getInt("sessionOrder"));
            assertEquals("cluster=eu;namespace=prod", event.getString("attributes"));
            assertEquals(1700000000000L, event.getInstant("provisionedAt").toEpochMilli());
            assertTrue(event.getInstant("jvmStartedAt").toEpochMilli() > 0,
                    "jvmStartedAt should be sourced from the running JVM");
        } finally {
            FlightRecorder.removePeriodicEvent(emitter);
            Files.deleteIfExists(file);
        }
    }

    private static List<RecordedEvent> readEvents(Path file) throws Exception {
        List<RecordedEvent> result = new ArrayList<>();
        try (RecordingFile recordingFile = new RecordingFile(file)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                if (EVENT_NAME.equals(event.getEventType().getName())) {
                    result.add(event);
                }
            }
        }
        return result;
    }
}
