/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.profile.manager.additional.AdditionalFileParser;
import pbouda.jeffrey.profile.manager.additional.PerfCountersAdditionalFileParser;
import pbouda.jeffrey.profile.manager.model.PerfCounter;
import pbouda.jeffrey.provider.profile.repository.ProfileCacheRepository;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdditionalFilesManagerImpl implements AdditionalFilesManager {

    private static final TypeReference<List<PerfCounter>> PERF_COUNTER_TYPE =
            new TypeReference<List<PerfCounter>>() {
            };

    public static final String PERF_COUNTERS_KEY = "performance_counters";

    private final ProfileCacheRepository cacheRepository;
    private final ProjectRecordingStorage projectRecordingStorage;

    private final Map<SupportedRecordingFile, AdditionalFileParser> parsers = Map.of(
            SupportedRecordingFile.PERF_COUNTERS, new PerfCountersAdditionalFileParser()
    );

    // Recording ID is set when processAdditionalFiles is called during profile initialization
    private String recordingId;

    // Cached heap dump path (lazy loaded)
    private Path heapDumpPath;
    private boolean heapDumpPathResolved;

    public AdditionalFilesManagerImpl(
            ProfileCacheRepository cacheRepository,
            ProjectRecordingStorage projectRecordingStorage) {

        this.cacheRepository = cacheRepository;
        this.projectRecordingStorage = projectRecordingStorage;
    }


    @Override
    public void processAdditionalFiles(String recordingId) {
        // Store recording ID for later use (e.g., heap dump path resolution)
        this.recordingId = recordingId;

        List<Path> findAdditionalFiles = projectRecordingStorage.findArtifacts(recordingId);
        for (Path additionalFile : findAdditionalFiles) {
            SupportedRecordingFile fileType = SupportedRecordingFile.of(additionalFile);
            AdditionalFileParser parser = parsers.get(fileType);
            if (parser != null) {
                parser.parse(additionalFile)
                        .ifPresent(content -> cacheRepository.put(PERF_COUNTERS_KEY, content));
            }
        }
    }

    @Override
    public boolean performanceCountersExists()  {
        return cacheRepository.contains(PERF_COUNTERS_KEY);
    }

    @Override
    public List<PerfCounter> performanceCounters() {
        return this.cacheRepository.get(PERF_COUNTERS_KEY, PERF_COUNTER_TYPE)
                .orElse(List.of());
    }

    @Override
    public boolean heapDumpExists() {
        return getHeapDumpPath().isPresent();
    }

    @Override
    public Optional<Path> getHeapDumpPath() {
        if (!heapDumpPathResolved) {
            resolveHeapDumpPath();
        }
        return Optional.ofNullable(heapDumpPath);
    }

    private synchronized void resolveHeapDumpPath() {
        if (heapDumpPathResolved) {
            return;
        }

        // PoC: Use hardcoded path for heap dump
        Path hardcodedPath = Path.of("/Users/petrbouda/heap-dump.hprof.gz");
        if (Files.exists(hardcodedPath)) {
            heapDumpPath = hardcodedPath;
            heapDumpPathResolved = true;
            return;
        }

        // Fallback to original behavior: look in recording storage
        if (recordingId == null) {
            // Recording ID not set yet - cannot resolve heap dump path
            return;
        }

        List<Path> artifacts = projectRecordingStorage.findArtifacts(recordingId);
        for (Path artifact : artifacts) {
            SupportedRecordingFile fileType = SupportedRecordingFile.of(artifact);
            if (fileType == SupportedRecordingFile.HEAP_DUMP || fileType == SupportedRecordingFile.HEAP_DUMP_GZ) {
                heapDumpPath = artifact;
                break;
            }
        }
        heapDumpPathResolved = true;
    }
}
