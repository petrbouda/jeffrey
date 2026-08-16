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

package cafe.jeffrey.jfr.events.trace;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one place tests record real JFR events: enable a type, run the body, dump, read back. Real
 * recordings on purpose — commit paths, {@code @Inherited} metadata and threshold behaviour are
 * exactly what these tests pin, and none of it is observable without going through JFR itself.
 */
final class JfrRecordings {

    private JfrRecordings() {
    }

    /** Every event of the type the body committed, in commit order. */
    static List<RecordedEvent> all(String eventType, Runnable body) throws IOException {
        Path dump = Files.createTempFile("jfr-recordings-test", ".jfr");
        try (Recording recording = new Recording()) {
            recording.enable(eventType).withThreshold(Duration.ZERO);
            recording.start();
            body.run();
            recording.stop();
            recording.dump(dump);

            return RecordingFile.readAllEvents(dump).stream()
                    .filter(event -> event.getEventType().getName().equals(eventType))
                    .toList();
        } finally {
            Files.deleteIfExists(dump);
        }
    }

    /** The one event the body committed, asserted to be exactly one. */
    static RecordedEvent single(String eventType, Runnable body) throws IOException {
        List<RecordedEvent> events = all(eventType, body);
        assertEquals(1, events.size(), "exactly the one committed event");
        return events.getFirst();
    }
}
