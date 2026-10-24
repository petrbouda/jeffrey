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

import tools.jackson.core.type.TypeReference;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PerfCountersManagerImpl implements PerfCountersManager {

    /** Where the counters are kept in the profile's cache; JFR profiles that carried a snapshot used it too. */
    public static final String PERF_COUNTERS_CACHE_KEY = "performance_counters";

    private static final TypeReference<List<PerfCounter>> PERF_COUNTERS_TYPE = new TypeReference<>() {
    };

    private final ProfileCacheRepository cacheRepository;
    private final PerfCountersCatalog catalog;

    public PerfCountersManagerImpl(ProfileCacheRepository cacheRepository, PerfCountersCatalog catalog) {
        this.cacheRepository = cacheRepository;
        this.catalog = catalog;
    }

    @Override
    public PerfCountersOverview ingest(Path perfDataFile) {
        Map<String, Object> values = PerfCountersParser.parse(perfDataFile);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("The perf-data file holds no counters: " + perfDataFile);
        }
        cacheRepository.put(PERF_COUNTERS_CACHE_KEY, catalog.describe(values));
        return PerfCountersOverviewBuilder.build(PerfCounterValues.of(values));
    }

    @Override
    public boolean exists() {
        return cacheRepository.contains(PERF_COUNTERS_CACHE_KEY);
    }

    @Override
    public List<PerfCounter> counters() {
        return cacheRepository.get(PERF_COUNTERS_CACHE_KEY, PERF_COUNTERS_TYPE).orElse(List.of());
    }

    @Override
    public Optional<PerfCountersOverview> overview() {
        List<PerfCounter> counters = counters();
        if (counters.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PerfCountersOverviewBuilder.build(PerfCounterValues.of(counters)));
    }
}
