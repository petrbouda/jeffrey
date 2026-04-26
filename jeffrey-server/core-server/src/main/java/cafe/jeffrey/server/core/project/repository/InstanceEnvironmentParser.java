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

package cafe.jeffrey.server.core.project.repository;

import tools.jackson.databind.node.ObjectNode;
import jdk.jfr.consumer.EventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.core.ServerJeffreyDirs;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.jfr.EventFieldsToJsonMapper;
import cafe.jeffrey.shared.common.model.EventTypeName;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a single JFR chunk and extracts the fixed set of one-shot
 * configuration / environment events we expose on the session-detail
 * endpoint. The result is an {@link ObjectNode} keyed by JFR event type name
 * whose values are the raw field maps emitted by
 * {@link EventFieldsToJsonMapper}. Each key is optional — it appears only if
 * the corresponding event was present in the chunk.
 */
public class InstanceEnvironmentParser {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceEnvironmentParser.class);

    private static final Set<String> ONE_SHOT_TYPES = Set.of(
            EventTypeName.JVM_INFORMATION,
            EventTypeName.OS_INFORMATION,
            EventTypeName.CPU_INFORMATION,
            EventTypeName.GC_CONFIGURATION,
            EventTypeName.GC_HEAP_CONFIGURATION,
            EventTypeName.COMPILER_CONFIGURATION,
            EventTypeName.CONTAINER_CONFIGURATION,
            EventTypeName.VIRTUALIZATION_INFORMATION);

    private final ServerJeffreyDirs serverJeffreyDirs;
    private final Lz4Compressor lz4Compressor;

    public InstanceEnvironmentParser(ServerJeffreyDirs serverJeffreyDirs) {
        this.serverJeffreyDirs = serverJeffreyDirs;
        this.lz4Compressor = new Lz4Compressor(serverJeffreyDirs);
    }

    /**
     * Parses the given JFR chunk and returns the extracted events. Accepts
     * both raw {@code .jfr} and LZ4-compressed {@code .jfr.lz4} files —
     * compressed chunks are decompressed into a scoped {@link TempDirectory}
     * that is wiped on return.
     *
     * @param jfrPath path to a JFR chunk on disk
     * @param expectShutdown when {@code true}, also looks for the
     *                       {@code jdk.Shutdown} event (present only in the
     *                       final chunk of a FINISHED session).
     * @return an outer ObjectNode keyed by JFR event type name; always
     *         non-null. Keys are present only for events found in the chunk,
     *         so the node may be empty if the chunk carried none of the
     *         one-shot types or if parsing failed.
     */
    public ObjectNode parse(Path jfrPath, boolean expectShutdown) {
        Set<String> needed = expectShutdown
                ? Stream.concat(ONE_SHOT_TYPES.stream(), Stream.of(EventTypeName.SHUTDOWN))
                        .collect(Collectors.toUnmodifiableSet())
                : ONE_SHOT_TYPES;

        if (Lz4Compressor.isLz4Compressed(jfrPath)) {
            try (TempDirectory td = serverJeffreyDirs.newTempDir()) {
                Path decompressed = lz4Compressor.decompressToDir(jfrPath, td.path());
                return readOneShotEvents(decompressed, needed);
            } catch (RuntimeException e) {
                LOG.warn("Failed to decompress JFR chunk for env extraction: path={}", jfrPath, e);
                return Json.createObject();
            }
        }
        return readOneShotEvents(jfrPath, needed);
    }

    private static ObjectNode readOneShotEvents(Path path, Set<String> needed) {
        ObjectNode result = Json.createObject();
        EventFieldsToJsonMapper mapper = new EventFieldsToJsonMapper();
        try (EventStream stream = EventStream.openFile(path)) {
            stream.onMetadata(metadata -> mapper.update(metadata.getEventTypes()));
            for (String type : needed) {
                stream.onEvent(type, e -> {
                    ObjectNode node = mapper.map(e);
                    // Drop the inherited jdk.jfr.Event fields — the environment
                    // cards show configuration data, not event-emission
                    // metadata. Without this, jdk.JVMInformation ends up with
                    // two "Start Time" rows (the JVM's real jvmStartTime plus
                    // the event's own startTime) that collide in the UI.
                    node.remove("startTime");
                    node.remove("duration");
                    // jdk.Shutdown has no wall-clock field of its own, so we
                    // re-inject the event's startTime under a dedicated key.
                    if (EventTypeName.SHUTDOWN.equals(type)) {
                        node.put("eventTime", e.getStartTime().toEpochMilli());
                    }
                    result.set(type, node);
                });
            }
            stream.start();
        } catch (IOException | RuntimeException e) {
            // Preserve whatever was already mapped — earlier chunks' config
            // events are still valid even if a later chunk trips the JDK
            // ChunkParser (e.g. NPE in logConstant).
            LOG.warn("Stopped reading JFR chunk at error — returning partial environment: path={} parsed_types={}",
                    path, iterableKeys(result), e);
        }
        return result;
    }

    private static String iterableKeys(ObjectNode node) {
        return node.propertyNames().toString();
    }
}
