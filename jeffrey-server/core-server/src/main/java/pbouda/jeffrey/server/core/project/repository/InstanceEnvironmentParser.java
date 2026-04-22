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

package pbouda.jeffrey.server.core.project.repository;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.shared.common.filesystem.TempDirectory;
import pbouda.jeffrey.shared.common.model.EventTypeName;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a single JFR chunk and extracts the fixed set of one-shot
 * configuration / environment events we expose on the Instance Detail
 * endpoint. Uses the JDK's native {@link RecordingFile} reader, so no
 * extra dependencies are required.
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
     * Parses the given JFR chunk and returns the extracted events. Accepts both
     * raw {@code .jfr} and LZ4-compressed {@code .jfr.lz4} files — compressed
     * chunks are decompressed into a scoped {@link TempDirectory} that is wiped
     * on return.
     *
     * @param jfrPath path to a JFR chunk on disk
     * @param expectShutdown when {@code true}, the reader scans to EOF so the
     *                       {@code jdk.Shutdown} event is not missed (it sits at
     *                       the end of a FINISHED instance's final chunk). When
     *                       {@code false}, the reader bails early as soon as all
     *                       one-shot config events have been seen.
     */
    public InstanceEnvironment parse(Path jfrPath, boolean expectShutdown) {
        Set<String> needed = expectShutdown
                ? Stream.concat(ONE_SHOT_TYPES.stream(), Stream.of(EventTypeName.SHUTDOWN))
                        .collect(Collectors.toUnmodifiableSet())
                : ONE_SHOT_TYPES;

        if (Lz4Compressor.isLz4Compressed(jfrPath)) {
            try (TempDirectory td = serverJeffreyDirs.newTempDir()) {
                Path decompressed = lz4Compressor.decompressToDir(jfrPath, td.path());
                return readOneShotEvents(decompressed, needed, expectShutdown);
            } catch (RuntimeException e) {
                LOG.warn("Failed to decompress JFR chunk for env extraction: path={}", jfrPath, e);
                return empty();
            }
        }
        return readOneShotEvents(jfrPath, needed, expectShutdown);
    }

    private static InstanceEnvironment readOneShotEvents(Path path, Set<String> needed, boolean expectShutdown) {
        Map<String, RecordedEvent> latest = new HashMap<>();
        try (RecordingFile rf = new RecordingFile(path)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                String typeName = event.getEventType().getName();
                if (needed.contains(typeName)) {
                    latest.put(typeName, event);
                }
                // Shutdown sits at EOF, so early-bail ONLY when not expecting it.
                if (!expectShutdown && latest.keySet().containsAll(ONE_SHOT_TYPES)) {
                    break;
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to parse JFR chunk: path={}", path, e);
            return empty();
        }

        return new InstanceEnvironment(
                Optional.ofNullable(latest.get(EventTypeName.JVM_INFORMATION)).map(InstanceEnvironmentParser::toJvm),
                Optional.ofNullable(latest.get(EventTypeName.OS_INFORMATION)).map(InstanceEnvironmentParser::toOs),
                Optional.ofNullable(latest.get(EventTypeName.CPU_INFORMATION)).map(InstanceEnvironmentParser::toCpu),
                Optional.ofNullable(latest.get(EventTypeName.GC_CONFIGURATION)).map(InstanceEnvironmentParser::toGc),
                Optional.ofNullable(latest.get(EventTypeName.GC_HEAP_CONFIGURATION)).map(InstanceEnvironmentParser::toGcHeap),
                Optional.ofNullable(latest.get(EventTypeName.COMPILER_CONFIGURATION)).map(InstanceEnvironmentParser::toCompiler),
                Optional.ofNullable(latest.get(EventTypeName.CONTAINER_CONFIGURATION)).map(InstanceEnvironmentParser::toContainer),
                Optional.ofNullable(latest.get(EventTypeName.VIRTUALIZATION_INFORMATION)).map(InstanceEnvironmentParser::toVirtualization),
                Optional.ofNullable(latest.get(EventTypeName.SHUTDOWN)).map(InstanceEnvironmentParser::toShutdown));
    }

    // ====================================================================
    // Per-event mappers. Every field read is guarded by hasField() because
    // JFR schemas drift between JDK versions.
    // ====================================================================

    private static JvmInformation toJvm(RecordedEvent e) {
        return new JvmInformation(
                getString(e, "jvmName"),
                getString(e, "jvmVersion"),
                getString(e, "jvmArguments"),
                getString(e, "jvmFlags"),
                getString(e, "javaArguments"),
                getInstantAsMillis(e, "jvmStartTime"),
                getLong(e, "pid"));
    }

    private static OsInformation toOs(RecordedEvent e) {
        return new OsInformation(getString(e, "osVersion"));
    }

    private static CpuInformation toCpu(RecordedEvent e) {
        return new CpuInformation(
                getString(e, "cpu"),
                getString(e, "description"),
                getInt(e, "sockets"),
                getInt(e, "cores"),
                getInt(e, "hwThreads"));
    }

    private static GcConfiguration toGc(RecordedEvent e) {
        return new GcConfiguration(
                getString(e, "youngCollector"),
                getString(e, "oldCollector"),
                getInt(e, "parallelGCThreads"),
                getInt(e, "concurrentGCThreads"),
                getBoolean(e, "usesDynamicGCThreads"),
                getBoolean(e, "isExplicitGCConcurrent"),
                getBoolean(e, "isExplicitGCDisabled"),
                getDurationAsMillis(e, "pauseTarget"),
                getInt(e, "gcTimeRatio"));
    }

    private static GcHeapConfiguration toGcHeap(RecordedEvent e) {
        return new GcHeapConfiguration(
                getLong(e, "minSize"),
                getLong(e, "maxSize"),
                getLong(e, "initialSize"),
                getBoolean(e, "usesCompressedOops"),
                getString(e, "compressedOopsMode"),
                getLong(e, "objectAlignment"),
                getInt(e, "heapAddressBits"));
    }

    private static CompilerConfiguration toCompiler(RecordedEvent e) {
        return new CompilerConfiguration(
                getInt(e, "threadCount"),
                getBoolean(e, "tieredCompilation"),
                getBoolean(e, "dynamicCompilerThreadCount"));
    }

    private static ContainerConfiguration toContainer(RecordedEvent e) {
        return new ContainerConfiguration(
                getString(e, "containerType"),
                getLong(e, "cpuSlicePeriod"),
                getLong(e, "cpuQuota"),
                getLong(e, "cpuShares"),
                getInt(e, "effectiveCpuCount"),
                getLong(e, "memorySoftLimit"),
                getLong(e, "memoryLimit"),
                getLong(e, "swapMemoryLimit"),
                getLong(e, "hostTotalMemory"),
                getLong(e, "hostTotalSwapMemory"));
    }

    private static VirtualizationInformation toVirtualization(RecordedEvent e) {
        return new VirtualizationInformation(getString(e, "name"));
    }

    private static ShutdownInfo toShutdown(RecordedEvent e) {
        Optional<String> reason = getString(e, "reason");
        return new ShutdownInfo(
                reason,
                Optional.of(e.getStartTime().toEpochMilli()),
                ShutdownKind.classify(reason.orElse(null)));
    }

    // ==============================
    // Defensive field extraction
    // ==============================

    private static Optional<String> getString(RecordedEvent e, String field) {
        if (!e.hasField(field)) return Optional.empty();
        return Optional.ofNullable(e.getString(field));
    }

    private static Optional<Long> getLong(RecordedEvent e, String field) {
        if (!e.hasField(field)) return Optional.empty();
        try {
            return Optional.of(e.getLong(field));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> getInt(RecordedEvent e, String field) {
        if (!e.hasField(field)) return Optional.empty();
        try {
            return Optional.of(e.getInt(field));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<Boolean> getBoolean(RecordedEvent e, String field) {
        if (!e.hasField(field)) return Optional.empty();
        try {
            return Optional.of(e.getBoolean(field));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<Long> getDurationAsMillis(RecordedEvent e, String field) {
        if (!e.hasField(field)) return Optional.empty();
        try {
            return Optional.of(e.getDuration(field).toMillis());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<Long> getInstantAsMillis(RecordedEvent e, String field) {
        if (!e.hasField(field)) return Optional.empty();
        try {
            return Optional.of(e.getInstant(field).toEpochMilli());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static InstanceEnvironment empty() {
        return new InstanceEnvironment(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}
