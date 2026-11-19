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

package cafe.jeffrey.microscope.core.profile.manager;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.core.utils.DurationUtils;
import cafe.jeffrey.microscope.core.utils.Json;
import cafe.jeffrey.microscope.core.profile.event.JITCompilationStats;
import cafe.jeffrey.microscope.core.profile.event.JITLongCompilation;
import cafe.jeffrey.microscope.core.profile.model.EventSummary;
import cafe.jeffrey.microscope.core.profile.model.ProfileInfo;
import cafe.jeffrey.microscope.core.profile.model.StacktraceType;
import cafe.jeffrey.microscope.core.profile.model.Type;
import cafe.jeffrey.microscope.core.profile.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.core.profile.manager.builder.JITLongCompilationBuilder;
import cafe.jeffrey.microscope.core.profile.manager.model.jit.CodeCacheData;
import cafe.jeffrey.microscope.core.profile.manager.model.jit.CodeCacheSegmentsBuilder;
import cafe.jeffrey.microscope.core.profile.manager.model.jit.CompilerQueueTimeseriesBuilder;
import cafe.jeffrey.microscope.core.profile.persistence.api.EventQueryConfigurer;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileEventRepository;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileEventStreamRepository;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileEventTypeRepository;
import cafe.jeffrey.microscope.core.profile.timeseries.SimpleTimeseriesBuilder;
import cafe.jeffrey.microscope.core.profile.timeseries.SingleSerie;
import cafe.jeffrey.microscope.core.profile.timeseries.TimeseriesData;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class JITCompilationManagerImpl implements JITCompilationManager {

    private static final String SETTING_THRESHOLD = "threshold";

    /** Reported when the recording does not carry a parseable compilation-threshold setting. */
    private static final long THRESHOLD_UNKNOWN_MILLIS = -1L;

    private final ProfileInfo profileInfo;
    private final ProfileEventTypeRepository eventTypeRepository;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public JITCompilationManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventTypeRepository eventTypeRepository,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {

        this.profileInfo = profileInfo;
        this.eventTypeRepository = eventTypeRepository;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public JITCompilationStats statistics() {
        Optional<ObjectNode> statsOpt = eventRepository.latestJsonFields(Type.COMPILER_STATISTICS);
        if (statsOpt.isEmpty()) {
            return null;
        }

        Optional<EventSummary> statisticsTypeOpt =
                eventTypeRepository.eventSummaries(Type.COMPILATION);

        if (statisticsTypeOpt.isEmpty()) {
            return null;
        }

        // Retrieve the threshold from the settings saying when the JIT compilation resulted in a long compilation
        // and ended up in compilation events. The setting may be absent or unparseable — in that case the
        // threshold is reported as unknown instead of feeding an unparseable default into the parser.
        Optional<Duration> threshold = Optional.ofNullable(statisticsTypeOpt.get().settings().get(SETTING_THRESHOLD))
                .map(value -> DurationUtils.parseOrDefault(value, null));

        ObjectNode fields = (ObjectNode) statsOpt.get();
        fields.put("compileMethodThreshold", threshold.map(Duration::toMillis).orElse(THRESHOLD_UNKNOWN_MILLIS));

        return Json.treeToValue(fields, JITCompilationStats.class);
    }

    @Override
    public List<JITLongCompilation> compilations(int limit) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.COMPILATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new JITLongCompilationBuilder(limit));
    }

    @Override
    public SingleSerie timeseries() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.EXECUTION_SAMPLE)
                .withTimeRange(timeRange)
                .filterStacktraceType(StacktraceType.JVM_JIT);

        TimeseriesData timeseriesData = eventStreamRepository
                .timeseriesStreamer(configurer, new SimpleTimeseriesBuilder("JIT Samples", timeRange));

        return timeseriesData.series().getFirst();
    }

    @Override
    public TimeseriesData compilerQueueTimeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        if (!eventRepository.containsEventType(Type.COMPILER_QUEUE_UTILIZATION)) {
            return TimeseriesData.empty();
        }

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.COMPILER_QUEUE_UTILIZATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new CompilerQueueTimeseriesBuilder(timeRange));
    }

    @Override
    public CodeCacheData codeCache() {
        // Last snapshot wins per code heap — requires a chronological stream.
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.CODE_CACHE_STATISTICS)
                .withJsonFields()
                .orderedByTime();

        var segments = eventStreamRepository.genericStreaming(configurer, new CodeCacheSegmentsBuilder());

        long codeCacheFullCount = eventRepository.containsEventType(Type.CODE_CACHE_FULL)
                ? eventRepository.eventsByTypeWithFields(Type.CODE_CACHE_FULL).size()
                : 0;

        return new CodeCacheData(segments, codeCacheFullCount);
    }
}
