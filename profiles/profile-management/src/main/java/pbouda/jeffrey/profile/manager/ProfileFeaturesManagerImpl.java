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

import pbouda.jeffrey.shared.model.EventSummary;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.profile.feature.FeatureCheckResult;
import pbouda.jeffrey.profile.feature.FeatureType;
import pbouda.jeffrey.profile.feature.checker.ContainerDashboardFeatureChecker;
import pbouda.jeffrey.profile.feature.checker.FeatureChecker;
import pbouda.jeffrey.profile.feature.checker.FeatureCheckers;
import pbouda.jeffrey.profile.feature.checker.PerfCounterDashboardFeatureChecker;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProfileFeaturesManagerImpl implements ProfileFeaturesManager {

    private final List<FeatureChecker> featureChecks;
    private final ProfileEventTypeRepository eventTypeRepository;

    public ProfileFeaturesManagerImpl(
            ProfileEventRepository eventRepository,
            ProfileEventTypeRepository eventTypeRepository,
            ProfileCacheRepository cacheRepository) {

        this.eventTypeRepository = eventTypeRepository;
        this.featureChecks = List.of(
                FeatureCheckers.HTTP_SERVER_DASHBOARD,
                FeatureCheckers.HTTP_CLIENT_DASHBOARD,
                FeatureCheckers.JDBC_STATEMENTS_DASHBOARD,
                FeatureCheckers.JDBC_POOL_DASHBOARD,
                FeatureCheckers.TRACING_DASHBOARD,
                new ContainerDashboardFeatureChecker(eventRepository),
                new PerfCounterDashboardFeatureChecker(cacheRepository));
    }

    @Override
    public List<FeatureType> getDisabledFeatures() {
        Map<Type, EventSummary> eventSummaries = this.eventTypeRepository.eventSummaries().stream()
                .collect(Collectors.toMap(es -> Type.fromCode(es.name()), Function.identity()));

        return featureChecks.stream()
                .map(checker -> checker.check(eventSummaries))
                .filter(result -> !result.enabled())
                .map(FeatureCheckResult::type)
                .toList();
    }
}
