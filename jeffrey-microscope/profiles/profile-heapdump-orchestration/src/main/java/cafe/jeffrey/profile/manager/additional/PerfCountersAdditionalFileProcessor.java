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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.manager.additional.PerfCounter;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PerfCountersAdditionalFileProcessor implements AdditionalFileProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(PerfCountersAdditionalFileProcessor.class);

    public static final String PERF_COUNTERS_CACHE_KEY = "performance_counters";

    @Override
    public Optional<ProcessingResult> process(Path filePath) {
        Map<String, Object> result;
        try {
            result = PerfCountersParser.parse(filePath);
        } catch (Exception e) {
            LOG.warn("Could not parse perf counters file: {}", e.getMessage());
            return Optional.empty();
        }

        if (result == null || result.isEmpty()) {
            LOG.warn("Perf counters file is empty or not valid");
            return Optional.empty();
        }

        // Loads additional description info to Performance Counters
        Map<String, String[]> perfCountersDescMap = loadPerfCountersDesc().stream()
                .collect(Collectors.toMap(parts -> parts[0], Function.identity()));

        List<PerfCounter> counters = new ArrayList<>();
        for (Map.Entry<String, Object> counterEntry : result.entrySet()) {
            String key = counterEntry.getKey();
            Object value = counterEntry.getValue();

            String[] descParts = perfCountersDescMap.get(key);
            if (descParts != null) {
                String datatype = descParts[1];
                String description = descParts[2];
                counters.add(new PerfCounter(key, value, datatype, description));
            } else {
                counters.add(new PerfCounter(key, value));
            }
        }

        return Optional.of(new ProcessingResult.CacheableResult(PERF_COUNTERS_CACHE_KEY, counters));
    }

    private static List<String[]> loadPerfCountersDesc() {
        String content = FileSystemUtils.readString("classpath:additional-info/perf-counters-desc.csv");
        return content.lines()
                .map(str -> str.split("\\|"))
                .toList();
    }

    @Override
    public ManagedFile managedFile() {
        return ManagedFile.PERF_COUNTERS;
    }
}
