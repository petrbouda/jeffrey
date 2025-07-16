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

package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.DurationUtils;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.EventSummary;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.manager.builder.JITLongCompilationBuilder;
import pbouda.jeffrey.manager.model.jit.JITCompilationStats;
import pbouda.jeffrey.manager.model.jit.JITLongCompilation;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.SimpleTimeseriesBuilder;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class JITCompilationManagerImpl implements JITCompilationManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventTypeRepository eventTypeRepository;
    private final ProfileEventRepository eventRepository;

    public JITCompilationManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventTypeRepository eventTypeRepository,
            ProfileEventRepository eventRepository) {

        this.profileInfo = profileInfo;
        this.eventTypeRepository = eventTypeRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public JITCompilationStats statistics() {
        Optional<GenericRecord> statsOpt = eventRepository.latest(Type.COMPILER_STATISTICS);
        if (statsOpt.isEmpty()) {
            return null;
        }

        Optional<EventSummary> statisticsTypeOpt =
                eventTypeRepository.eventSummaries(Type.COMPILATION);

        if (statisticsTypeOpt.isEmpty()) {
            return null;
        }

        // Retrieve the threshold from the settings saying when the JIT compilation resulted in a long compilation
        // and ended up in compilation events.
        String thresholdInString = statisticsTypeOpt.get().settings()
                .getOrDefault("threshold", "-1");
        Duration threshold = DurationUtils.parse(thresholdInString);

        ObjectNode fields = statsOpt.get().jsonFields();
        fields.put("compileMethodThreshold", threshold.toMillis());

        return Json.treeToValue(fields, JITCompilationStats.class);
    }

    @Override
    public List<JITLongCompilation> compilations(int limit) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.COMPILATION)
                .withJsonFields();

        return eventRepository.newEventStreamerFactory(configurer)
                .newGenericStreamer()
                .startStreaming(new JITLongCompilationBuilder(limit));
    }

    @Override
    public SingleSerie timeseries() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.EXECUTION_SAMPLE)
                .withTimeRange(timeRange)
                .filterStacktraceType(StacktraceType.JVM_JIT);

        TimeseriesData timeseriesData = eventRepository.newEventStreamerFactory(configurer)
                .newSimpleTimeseriesStreamer()
                .startStreaming(new SimpleTimeseriesBuilder("JIT Samples", timeRange));

        return timeseriesData.series().getFirst();
    }
}
