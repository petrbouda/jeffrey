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

package cafe.jeffrey.profile.manager.additional;

import tools.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.profile.manager.additional.AdditionalFileProcessor;
import cafe.jeffrey.profile.manager.additional.HeapDumpAdditionalFileProcessor;
import cafe.jeffrey.profile.manager.additional.PerfCountersAdditionalFileProcessor;
import cafe.jeffrey.profile.manager.additional.ProcessingResult;
import cafe.jeffrey.profile.manager.additional.PerfCounter;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;

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
    private final Path heapDumpAnalysisPath;
    private final Map<ManagedFile, AdditionalFileProcessor> processors;

    // Cached heap dump path (lazy loaded)
    private Path heapDumpPath;
    private boolean heapDumpPathResolved;

    public AdditionalFilesManagerImpl(
            ProfileCacheRepository cacheRepository,
            Path heapDumpAnalysisPath) {

        this.cacheRepository = cacheRepository;
        this.heapDumpAnalysisPath = heapDumpAnalysisPath;

        // Initialize processors map with all supported processors
        this.processors = Map.of(
                ManagedFile.PERF_COUNTERS, new PerfCountersAdditionalFileProcessor(),
                ManagedFile.HEAP_DUMP, new HeapDumpAdditionalFileProcessor(heapDumpAnalysisPath, ManagedFile.HEAP_DUMP),
                ManagedFile.HEAP_DUMP_GZ, new HeapDumpAdditionalFileProcessor(heapDumpAnalysisPath, ManagedFile.HEAP_DUMP_GZ)
        );
    }


    @Override
    public void processAdditionalFiles(List<Path> artifacts) {
        for (Path additionalFile : artifacts) {
            ManagedFile fileType = ManagedFile.of(additionalFile);
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

        // Look for heap dump in profile's heap-dump folder
        if (Files.exists(heapDumpAnalysisPath) && Files.isDirectory(heapDumpAnalysisPath)) {
            try (var files = Files.list(heapDumpAnalysisPath)) {
                Optional<Path> found = files
                        .filter(file -> {
                            ManagedFile fileType = ManagedFile.of(file);
                            return fileType == ManagedFile.HEAP_DUMP ||
                                    fileType == ManagedFile.HEAP_DUMP_GZ;
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
