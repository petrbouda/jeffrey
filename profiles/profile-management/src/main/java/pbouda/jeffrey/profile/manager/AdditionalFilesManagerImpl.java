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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.StringAnalysisReport;
import pbouda.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STRING_ANALYSIS_FILE = "string-analysis.json";
    private static final String THREAD_ANALYSIS_FILE = "thread-analysis.json";

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

    @Override
    public boolean stringAnalysisExists() {
        Path stringAnalysisPath = heapDumpAnalysisPath.resolve(STRING_ANALYSIS_FILE);
        return Files.exists(stringAnalysisPath);
    }

    @Override
    public Optional<StringAnalysisReport> getStringAnalysis() {
        Path stringAnalysisPath = heapDumpAnalysisPath.resolve(STRING_ANALYSIS_FILE);
        if (!Files.exists(stringAnalysisPath)) {
            return Optional.empty();
        }

        try {
            StringAnalysisReport report = OBJECT_MAPPER.readValue(
                    stringAnalysisPath.toFile(), StringAnalysisReport.class);
            return Optional.of(report);
        } catch (IOException e) {
            LOG.error("Failed to read string analysis: path={}", stringAnalysisPath, e);
            return Optional.empty();
        }
    }

    @Override
    public void saveStringAnalysis(StringAnalysisReport report) {
        try {
            // Create the directory if it doesn't exist
            Files.createDirectories(heapDumpAnalysisPath);

            Path stringAnalysisPath = heapDumpAnalysisPath.resolve(STRING_ANALYSIS_FILE);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(stringAnalysisPath.toFile(), report);

            LOG.info("String analysis saved: path={}", stringAnalysisPath);
        } catch (IOException e) {
            LOG.error("Failed to save string analysis: path={}", heapDumpAnalysisPath, e);
            throw new RuntimeException("Failed to save string analysis: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteStringAnalysis() {
        Path stringAnalysisPath = heapDumpAnalysisPath.resolve(STRING_ANALYSIS_FILE);
        if (Files.exists(stringAnalysisPath)) {
            try {
                Files.delete(stringAnalysisPath);
                LOG.info("String analysis deleted: path={}", stringAnalysisPath);
            } catch (IOException e) {
                LOG.error("Failed to delete string analysis: path={}", stringAnalysisPath, e);
            }
        }
    }

    @Override
    public boolean threadAnalysisExists() {
        Path threadAnalysisPath = heapDumpAnalysisPath.resolve(THREAD_ANALYSIS_FILE);
        return Files.exists(threadAnalysisPath);
    }

    @Override
    public Optional<ThreadAnalysisReport> getThreadAnalysis() {
        Path threadAnalysisPath = heapDumpAnalysisPath.resolve(THREAD_ANALYSIS_FILE);
        if (!Files.exists(threadAnalysisPath)) {
            return Optional.empty();
        }

        try {
            ThreadAnalysisReport report = OBJECT_MAPPER.readValue(
                    threadAnalysisPath.toFile(), ThreadAnalysisReport.class);
            return Optional.of(report);
        } catch (IOException e) {
            LOG.error("Failed to read thread analysis: path={}", threadAnalysisPath, e);
            return Optional.empty();
        }
    }

    @Override
    public void saveThreadAnalysis(ThreadAnalysisReport report) {
        try {
            // Create the directory if it doesn't exist
            Files.createDirectories(heapDumpAnalysisPath);

            Path threadAnalysisPath = heapDumpAnalysisPath.resolve(THREAD_ANALYSIS_FILE);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(threadAnalysisPath.toFile(), report);

            LOG.info("Thread analysis saved: path={}", threadAnalysisPath);
        } catch (IOException e) {
            LOG.error("Failed to save thread analysis: path={}", heapDumpAnalysisPath, e);
            throw new RuntimeException("Failed to save thread analysis: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteThreadAnalysis() {
        Path threadAnalysisPath = heapDumpAnalysisPath.resolve(THREAD_ANALYSIS_FILE);
        if (Files.exists(threadAnalysisPath)) {
            try {
                Files.delete(threadAnalysisPath);
                LOG.info("Thread analysis deleted: path={}", threadAnalysisPath);
            } catch (IOException e) {
                LOG.error("Failed to delete thread analysis: path={}", threadAnalysisPath, e);
            }
        }
    }
}
