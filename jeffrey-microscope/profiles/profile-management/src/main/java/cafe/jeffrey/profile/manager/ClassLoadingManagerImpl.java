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

package cafe.jeffrey.profile.manager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadActivity;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadActivityBuilder;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoaderStat;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoaderStatsBuilder;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadingOverview;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadingTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.classloading.ClassRedefinitionStat;
import cafe.jeffrey.profile.manager.model.classloading.RedefinitionData;
import cafe.jeffrey.profile.manager.model.classloading.RetransformBatch;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.Optional;

public class ClassLoadingManagerImpl implements ClassLoadingManager {

    private static final int MAX_SLOWEST_CLASS_LOADS = 50;

    private static final String LOADED_CLASS_COUNT_FIELD = "loadedClassCount";
    private static final String UNLOADED_CLASS_COUNT_FIELD = "unloadedClassCount";
    private static final String REDEFINED_CLASS_FIELD = "redefinedClass";
    private static final String CLASS_MODIFICATION_COUNT_FIELD = "classModificationCount";
    private static final String REDEFINITION_ID_FIELD = "redefinitionId";
    private static final String CLASS_COUNT_FIELD = "classCount";
    private static final String DURATION_FIELD = "duration";

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public ClassLoadingManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public ClassLoadingOverview overview() {
        Optional<ObjectNode> latest = eventRepository.latestJsonFields(Type.CLASS_LOADING_STATISTICS);
        long totalLoaded = latest.map(fields -> Math.max(0, Json.readLong(fields, LOADED_CLASS_COUNT_FIELD))).orElse(0L);
        long totalUnloaded = latest.map(fields -> Math.max(0, Json.readLong(fields, UNLOADED_CLASS_COUNT_FIELD))).orElse(0L);
        long currentlyLoaded = Math.max(0, totalLoaded - totalUnloaded);

        List<ClassLoaderStat> loaders = classLoaders();
        long metaspaceUsed = loaders.stream().mapToLong(ClassLoaderStat::metaspaceBytes).sum();
        long hiddenClasses = loaders.stream().mapToLong(ClassLoaderStat::hiddenClassCount).sum();

        return new ClassLoadingOverview(
                currentlyLoaded,
                totalLoaded,
                totalUnloaded,
                loaders.size(),
                metaspaceUsed,
                hiddenClasses,
                eventRepository.containsEventType(Type.CLASS_LOAD),
                eventRepository.containsEventType(Type.CLASS_REDEFINITION));
    }

    @Override
    public TimeseriesData timeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.CLASS_LOADING_STATISTICS)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new ClassLoadingTimeseriesBuilder(timeRange));
    }

    @Override
    public List<ClassLoaderStat> classLoaders() {
        // Time order so the last snapshot wins per loader (latest-state semantics).
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.CLASS_LOADER_STATISTICS)
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new ClassLoaderStatsBuilder());
    }

    @Override
    public ClassLoadActivity classLoadActivity() {
        if (!eventRepository.containsEventType(Type.CLASS_LOAD)) {
            return ClassLoadActivity.empty();
        }

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.CLASS_LOAD)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new ClassLoadActivityBuilder(MAX_SLOWEST_CLASS_LOADS));
    }

    @Override
    public RedefinitionData redefinitions() {
        List<ClassRedefinitionStat> redefinitions = eventRepository.eventsByTypeWithFields(Type.CLASS_REDEFINITION).stream()
                .map(ClassLoadingManagerImpl::toRedefinitionStat)
                .toList();

        List<RetransformBatch> retransforms = eventRepository.eventsByTypeWithFields(Type.RETRANSFORM_CLASSES).stream()
                .map(ClassLoadingManagerImpl::toRetransformBatch)
                .toList();

        return new RedefinitionData(redefinitions, retransforms);
    }

    private static ClassRedefinitionStat toRedefinitionStat(JsonNode fields) {
        return new ClassRedefinitionStat(
                Json.readString(fields, REDEFINED_CLASS_FIELD),
                Math.max(0, Json.readInt(fields, CLASS_MODIFICATION_COUNT_FIELD)),
                Json.readLong(fields, REDEFINITION_ID_FIELD));
    }

    private static RetransformBatch toRetransformBatch(JsonNode fields) {
        return new RetransformBatch(
                Json.readLong(fields, REDEFINITION_ID_FIELD),
                Math.max(0, Json.readInt(fields, CLASS_COUNT_FIELD)),
                Math.max(0, Json.readLong(fields, DURATION_FIELD)));
    }
}
