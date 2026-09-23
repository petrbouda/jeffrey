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

package cafe.jeffrey.profile.feature.checker;

import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.feature.FeatureCheckResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.additional.PerfCountersAdditionalFileProcessor;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;

import java.util.Map;

public class PerfCounterDashboardFeatureChecker implements FeatureChecker {

    private final ProfileCacheRepository cacheRepository;

    public PerfCounterDashboardFeatureChecker(ProfileCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public FeatureCheckResult check(Map<Type, EventSummary> eventSummaries) {
        boolean enabled = cacheRepository.contains(PerfCountersAdditionalFileProcessor.PERF_COUNTERS_CACHE_KEY);
        return new FeatureCheckResult(FeatureType.PERF_COUNTERS_DASHBOARD, enabled);
    }
}
