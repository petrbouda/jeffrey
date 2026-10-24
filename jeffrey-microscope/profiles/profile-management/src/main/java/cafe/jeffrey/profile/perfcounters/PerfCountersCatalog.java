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

package cafe.jeffrey.profile.perfcounters;

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What the counters mean: a datatype and a description per counter name, read from
 * {@code additional-info/perf-counters-desc.csv} ({@code name|datatype|description}). An entry ending
 * in {@code .*} describes every counter under that prefix, so a space numbered at runtime is still
 * described.
 */
public final class PerfCountersCatalog {

    private static final String CATALOG_PATH = "classpath:additional-info/perf-counters-desc.csv";
    private static final Pattern SEPARATOR = Pattern.compile("\\|");
    private static final String WILDCARD_SUFFIX = ".*";
    private static final int FIELD_COUNT = 3;

    private record Description(String datatype, String text) {
    }

    private final Map<String, Description> exact;
    private final Map<String, Description> byPrefix;

    private PerfCountersCatalog(Map<String, Description> exact, Map<String, Description> byPrefix) {
        this.exact = Map.copyOf(exact);
        this.byPrefix = Map.copyOf(byPrefix);
    }

    public static PerfCountersCatalog load() {
        return parse(FileSystemUtils.readString(CATALOG_PATH));
    }

    static PerfCountersCatalog parse(String content) {
        Map<String, Description> exact = new HashMap<>();
        Map<String, Description> byPrefix = new HashMap<>();
        content.lines()
                .map(SEPARATOR::split)
                .filter(fields -> fields.length == FIELD_COUNT)
                .forEach(fields -> {
                    Description description = new Description(fields[1], fields[2]);
                    if (fields[0].endsWith(WILDCARD_SUFFIX)) {
                        String prefix = fields[0].substring(0, fields[0].length() - WILDCARD_SUFFIX.length() + 1);
                        byPrefix.put(prefix, description);
                    } else {
                        exact.put(fields[0], description);
                    }
                });
        return new PerfCountersCatalog(exact, byPrefix);
    }

    /** Every counter of the snapshot with its description where the catalog has one, sorted by name. */
    public List<PerfCounter> describe(Map<String, Object> counters) {
        return counters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> find(entry.getKey())
                        .map(description -> new PerfCounter(
                                entry.getKey(), entry.getValue(), description.datatype(), description.text()))
                        .orElseGet(() -> new PerfCounter(entry.getKey(), entry.getValue())))
                .toList();
    }

    private Optional<Description> find(String name) {
        Description description = exact.get(name);
        if (description != null) {
            return Optional.of(description);
        }
        // The longest prefix is the most specific description.
        return byPrefix.entrySet().stream()
                .filter(entry -> name.startsWith(entry.getKey()))
                .max(Comparator.comparingInt(entry -> entry.getKey().length()))
                .map(Map.Entry::getValue);
    }
}
