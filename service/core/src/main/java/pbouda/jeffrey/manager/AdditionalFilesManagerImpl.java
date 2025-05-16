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

package pbouda.jeffrey.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.manager.additional.AdditionalFileParser;
import pbouda.jeffrey.manager.additional.PerfCountersAdditionalFileParser;
import pbouda.jeffrey.manager.model.PerfCounter;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class AdditionalFilesManagerImpl implements AdditionalFilesManager {

    private static final TypeReference<List<PerfCounter>> PERF_COUNTER_TYPE =
            new TypeReference<List<PerfCounter>>() {
            };

    private static final String PERF_COUNTERS_KEY = "performance_counters";

    private final ProfileCacheRepository cacheRepository;
    private final ProjectRecordingStorage projectRecordingStorage;

    private final Map<SupportedRecordingFile, AdditionalFileParser> parsers = Map.of(
            SupportedRecordingFile.PERF_COUNTERS, new PerfCountersAdditionalFileParser()
    );

    public AdditionalFilesManagerImpl(
            ProfileCacheRepository cacheRepository,
            ProjectRecordingStorage projectRecordingStorage) {

        this.cacheRepository = cacheRepository;
        this.projectRecordingStorage = projectRecordingStorage;
    }


    @Override
    public void processAdditionalFiles(String recordingId) {
        List<Path> findAdditionalFiles = projectRecordingStorage.findAdditionalFiles(recordingId);
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
    public boolean performanceCountersExists() {
        return cacheRepository.contains(PERF_COUNTERS_KEY);
    }

    @Override
    public List<PerfCounter> performanceCounters() {
        return this.cacheRepository.get(PERF_COUNTERS_KEY, PERF_COUNTER_TYPE)
                .orElse(List.of());
    }
}
