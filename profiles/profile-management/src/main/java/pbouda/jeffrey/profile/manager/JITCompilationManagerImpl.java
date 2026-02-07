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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.DurationUtils;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.profile.common.event.JITCompilationStats;
import pbouda.jeffrey.profile.common.event.JITLongCompilation;
import pbouda.jeffrey.shared.common.model.EventSummary;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.StacktraceType;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.profile.manager.builder.JITLongCompilationBuilder;
import pbouda.jeffrey.provider.profile.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.profile.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.profile.repository.ProfileEventStreamRepository;
import pbouda.jeffrey.provider.profile.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.timeseries.SimpleTimeseriesBuilder;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class JITCompilationManagerImpl implements JITCompilationManager {

    private static final Logger LOG = LoggerFactory.getLogger(JITCompilationManagerImpl.class);

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
        LOG.debug("Fetching JIT compilation statistics");
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
        // and ended up in compilation events.
        String thresholdInString = statisticsTypeOpt.get().settings()
                .getOrDefault("threshold", "-1");
        Duration threshold = DurationUtils.parse(thresholdInString);

        ObjectNode fields = (ObjectNode) statsOpt.get();
        fields.put("compileMethodThreshold", threshold.toMillis());

        return Json.treeToValue(fields, JITCompilationStats.class);
    }

    @Override
    public List<JITLongCompilation> compilations(int limit) {
        LOG.debug("Fetching JIT compilations: limit={}", limit);
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.COMPILATION)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new JITLongCompilationBuilder(limit));
    }

    @Override
    public SingleSerie timeseries() {
        LOG.debug("Fetching JIT compilation timeseries");
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.EXECUTION_SAMPLE)
                .withTimeRange(timeRange)
                .filterStacktraceType(StacktraceType.JVM_JIT);

        TimeseriesData timeseriesData = eventStreamRepository
                .timeseriesStreamer(configurer, new SimpleTimeseriesBuilder("JIT Samples", timeRange));

        return timeseriesData.series().getFirst();
    }
}
