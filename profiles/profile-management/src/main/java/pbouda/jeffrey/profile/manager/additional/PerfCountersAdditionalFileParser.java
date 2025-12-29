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

package pbouda.jeffrey.profile.manager.additional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.profile.manager.model.PerfCounter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PerfCountersAdditionalFileParser implements AdditionalFileParser {

    private static final Logger LOG = LoggerFactory.getLogger(PerfCountersAdditionalFileParser.class);

    @Override
    public Optional<Object> parse(Path filePath) {
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

        return Optional.of(counters);
    }

    private static List<String[]> loadPerfCountersDesc() {
        String content = FileSystemUtils.readString("classpath:additional-info/perf-counters-desc.csv");
        return content.lines()
                .map(str -> str.split("\\|"))
                .toList();
    }

    @Override
    public SupportedRecordingFile supportedRecordingFile() {
        return SupportedRecordingFile.PERF_COUNTERS;
    }
}
