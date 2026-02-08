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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.profile.manager.additional.AdditionalFileProcessor;
import pbouda.jeffrey.profile.manager.additional.HeapDumpAdditionalFileProcessor;
import pbouda.jeffrey.profile.manager.additional.PerfCountersAdditionalFileProcessor;
import pbouda.jeffrey.profile.manager.additional.ProcessingResult;
import pbouda.jeffrey.profile.manager.model.PerfCounter;
import pbouda.jeffrey.provider.profile.repository.ProfileCacheRepository;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdditionalFilesManagerImpl implements AdditionalFilesManager {

    private static final Logger LOG = LoggerFactory.getLogger(AdditionalFilesManagerImpl.class);

    private static final TypeReference<List<PerfCounter>> PERF_COUNTER_TYPE =
            new TypeReference<List<PerfCounter>>() {
            };

    private final ProfileCacheRepository cacheRepository;
    private final ProjectRecordingStorage projectRecordingStorage;
    private final Path heapDumpAnalysisPath;
    private final Map<SupportedRecordingFile, AdditionalFileProcessor> processors;

    // Cached heap dump path (lazy loaded)
    private Path heapDumpPath;
    private boolean heapDumpPathResolved;

    public AdditionalFilesManagerImpl(
            ProfileCacheRepository cacheRepository,
            ProjectRecordingStorage projectRecordingStorage,
            JeffreyDirs jeffreyDirs,
            String profileId) {

        this.cacheRepository = cacheRepository;
        this.projectRecordingStorage = projectRecordingStorage;
        this.heapDumpAnalysisPath = jeffreyDirs.heapDumpAnalysisDir(profileId);

        // Initialize processors map with all supported processors
        this.processors = Map.of(
                SupportedRecordingFile.PERF_COUNTERS, new PerfCountersAdditionalFileProcessor(),
                SupportedRecordingFile.HEAP_DUMP, new HeapDumpAdditionalFileProcessor(heapDumpAnalysisPath, SupportedRecordingFile.HEAP_DUMP),
                SupportedRecordingFile.HEAP_DUMP_GZ, new HeapDumpAdditionalFileProcessor(heapDumpAnalysisPath, SupportedRecordingFile.HEAP_DUMP_GZ)
        );
    }


    @Override
    public void processAdditionalFiles(String recordingId) {
        List<Path> findAdditionalFiles = projectRecordingStorage.findArtifacts(recordingId);
        for (Path additionalFile : findAdditionalFiles) {
            SupportedRecordingFile fileType = SupportedRecordingFile.of(additionalFile);
            AdditionalFileProcessor processor = processors.get(fileType);
            if (processor != null) {
                processor.process(additionalFile)
                        .ifPresent(this::handleResult);
            }
        }
    }

    private void handleResult(ProcessingResult result) {
        switch (result) {
            case ProcessingResult.CacheableResult cacheable ->
                    cacheRepository.put(cacheable.cacheKey(), cacheable.content());
            case ProcessingResult.FileTransferResult fileTransfer ->
                    LOG.debug("File transferred: destination={}", fileTransfer.destinationPath());
            case ProcessingResult.NoOpResult _ ->
                    LOG.debug("No operation performed");
        }
    }

    @Override
    public boolean performanceCountersExists() {
        return cacheRepository.contains(PerfCountersAdditionalFileProcessor.PERF_COUNTERS_CACHE_KEY);
    }

    @Override
    public List<PerfCounter> performanceCounters() {
        return this.cacheRepository.get(PerfCountersAdditionalFileProcessor.PERF_COUNTERS_CACHE_KEY, PERF_COUNTER_TYPE)
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

        // Look for heap dump in profile's heap-dump-analysis folder
        if (Files.exists(heapDumpAnalysisPath) && Files.isDirectory(heapDumpAnalysisPath)) {
            try (var files = Files.list(heapDumpAnalysisPath)) {
                Optional<Path> found = files
                        .filter(file -> {
                            SupportedRecordingFile fileType = SupportedRecordingFile.of(file);
                            return fileType == SupportedRecordingFile.HEAP_DUMP ||
                                    fileType == SupportedRecordingFile.HEAP_DUMP_GZ;
                        })
                        .findFirst();
                if (found.isPresent()) {
                    heapDumpPath = found.get();
                }
            } catch (IOException e) {
                // Heap dump not accessible - heapDumpPath remains null
            }
        }
        heapDumpPathResolved = true;
    }

    @Override
    public Path getHeapDumpAnalysisPath() {
        return heapDumpAnalysisPath;
    }
}
