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

package pbouda.jeffrey.profile.feature.checker;

import pbouda.jeffrey.shared.model.EventSummary;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.profile.feature.FeatureCheckResult;
import pbouda.jeffrey.profile.feature.FeatureType;
import pbouda.jeffrey.profile.manager.AdditionalFilesManagerImpl;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;

import java.util.Map;

public class PerfCounterDashboardFeatureChecker implements FeatureChecker {

    private final ProfileCacheRepository cacheRepository;

    public PerfCounterDashboardFeatureChecker(ProfileCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public FeatureCheckResult check(Map<Type, EventSummary> eventSummaries) {
        boolean enabled = cacheRepository.contains(AdditionalFilesManagerImpl.PERF_COUNTERS_KEY);
        return new FeatureCheckResult(FeatureType.PERF_COUNTERS_DASHBOARD, enabled);
    }
}
