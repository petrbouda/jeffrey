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

package cafe.jeffrey.hub.client.environment;

import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.microscope.model.jfr.EventFieldsToJsonMapper;
import cafe.jeffrey.microscope.model.jfr.MappedFields;
import cafe.jeffrey.shared.common.model.EventTypeName;
import jdk.jfr.consumer.EventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a single JFR chunk and extracts the fixed set of one-shot configuration / environment
 * events the session-detail page shows. The result is an {@link ObjectNode} keyed by JFR event
 * type name whose values are the raw field maps emitted by {@link EventFieldsToJsonMapper}. Each
 * key is optional — it appears only if the corresponding event was present in the chunk.
 *
 * <p>This runs in Microscope, on a chunk pulled off the hub, rather than on the hub itself. The
 * hub serves the file and knows nothing about what is in it: it holds no JFR parser, and the only
 * consumer of this data is Microscope's hub browser.
 */
public class SessionEnvironmentParser {

    private static final Logger LOG = LoggerFactory.getLogger(SessionEnvironmentParser.class);

    private static final Set<String> ONE_SHOT_TYPES = Set.of(
            EventTypeName.JVM_INFORMATION,
            EventTypeName.OS_INFORMATION,
            EventTypeName.CPU_INFORMATION,
            EventTypeName.GC_CONFIGURATION,
            EventTypeName.GC_HEAP_CONFIGURATION,
            EventTypeName.COMPILER_CONFIGURATION,
            EventTypeName.CONTAINER_CONFIGURATION,
            EventTypeName.VIRTUALIZATION_INFORMATION);

    private final TempDirFactory tempDirFactory;
    private final Lz4Compressor lz4Compressor;

    public SessionEnvironmentParser(TempDirFactory tempDirFactory) {
        this.tempDirFactory = tempDirFactory;
        this.lz4Compressor = new Lz4Compressor(tempDirFactory);
    }

    /**
     * Parses the given JFR chunk and returns the extracted events. Accepts both raw {@code .jfr}
     * and LZ4-compressed {@code .jfr.lz4} files — the hub compresses a finished chunk, and
     * {@link EventStream} cannot open one, so a compressed chunk is decompressed into a scoped
     * {@link TempDirectory} that is wiped on return.
     *
     * @param jfrPath        path to a JFR chunk on disk
     * @param expectShutdown when {@code true}, also looks for the {@code jdk.Shutdown} event,
     *                       which is present only in the final chunk of a FINISHED session
     * @return an outer ObjectNode keyed by JFR event type name; always non-null. Keys are present
     * only for events found in the chunk, so the node may be empty if the chunk carried none of
     * the one-shot types or if parsing failed.
     */
    public ObjectNode parse(Path jfrPath, boolean expectShutdown) {
        Set<String> needed = expectShutdown
                ? Stream.concat(ONE_SHOT_TYPES.stream(), Stream.of(EventTypeName.SHUTDOWN))
                        .collect(Collectors.toUnmodifiableSet())
                : ONE_SHOT_TYPES;

        if (Lz4Compressor.isLz4Compressed(jfrPath)) {
            try (TempDirectory td = tempDirFactory.newTempDir()) {
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
                    ObjectNode node = toFullTree(mapper.map(e));
                    // Drop the inherited jdk.jfr.Event fields — the environment cards show
                    // configuration data, not event-emission metadata. Without this,
                    // jdk.JVMInformation ends up with two "Start Time" rows (the JVM's real
                    // jvmStartTime plus the event's own startTime) that collide in the UI.
                    node.remove("startTime");
                    node.remove("duration");
                    // jdk.Shutdown has no wall-clock field of its own, so we re-inject the
                    // event's startTime under a dedicated key.
                    if (EventTypeName.SHUTDOWN.equals(type)) {
                        node.put("eventTime", e.getStartTime().toEpochMilli());
                    }
                    result.set(type, node);
                });
            }
            stream.start();
        } catch (IOException | RuntimeException e) {
            // Preserve whatever was already mapped — earlier chunks' config events are still
            // valid even if a later chunk trips the JDK ChunkParser (e.g. NPE in logConstant).
            LOG.warn("Stopped reading JFR chunk at error — returning partial environment: path={} parsed_types={}",
                    path, iterableKeys(result), e);
        }
        return result;
    }

    /**
     * Rebuilds the event's complete field tree from what the mapper produced: its JSON with the
     * pooled value spliced back under the key it was lifted from. The mapper lifts the single
     * largest text field out of the JSON so the parser can pool it in the profile database; the
     * environment cards read the event directly and need that value back in place.
     */
    private static ObjectNode toFullTree(MappedFields mapped) {
        ObjectNode node = Json.readObjectNode(mapped.json());
        if (mapped.hasPooledField()) {
            node.put(mapped.pooledField(), mapped.pooledText());
        }
        return node;
    }

    private static String iterableKeys(ObjectNode node) {
        return node.propertyNames().toString();
    }
}
