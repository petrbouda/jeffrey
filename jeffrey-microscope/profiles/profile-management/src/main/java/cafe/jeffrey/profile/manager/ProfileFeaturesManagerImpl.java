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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.feature.FeatureCheckResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.feature.checker.ContainerDashboardFeatureChecker;
import cafe.jeffrey.profile.feature.checker.FeatureChecker;
import cafe.jeffrey.profile.feature.checker.FeatureCheckers;
import cafe.jeffrey.profile.feature.checker.PerfCounterDashboardFeatureChecker;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.profile.feature.checker.TracesFeatureChecker;
import cafe.jeffrey.provider.profile.api.TraceRepository;

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
            ProfileCacheRepository cacheRepository,
            TraceRepository traceRepository) {

        this.eventTypeRepository = eventTypeRepository;
        this.featureChecks = List.of(
                FeatureCheckers.HTTP_SERVER_DASHBOARD,
                FeatureCheckers.HTTP_CLIENT_DASHBOARD,
                FeatureCheckers.GRPC_SERVER_DASHBOARD,
                FeatureCheckers.GRPC_CLIENT_DASHBOARD,
                FeatureCheckers.JDBC_STATEMENTS_DASHBOARD,
                FeatureCheckers.JDBC_POOL_DASHBOARD,
                FeatureCheckers.METHOD_TRACING_DASHBOARD,
                FeatureCheckers.ASYNC_PROFILER_SPANS,
                new ContainerDashboardFeatureChecker(eventRepository),
                new PerfCounterDashboardFeatureChecker(cacheRepository),
                new TracesFeatureChecker(traceRepository));
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
