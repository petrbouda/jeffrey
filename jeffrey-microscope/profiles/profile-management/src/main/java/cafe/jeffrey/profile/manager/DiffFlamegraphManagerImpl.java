/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.flamegraph.ai.AiExportConfig;

import cafe.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import cafe.jeffrey.flamegraph.diff.ProfileComparison;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.SpanScope;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DiffFlamegraphManagerImpl implements DifferentialFlamegraphManager {

    private static final List<Type> SUPPORTED_EVENTS = List.of(
            Type.EXECUTION_SAMPLE,
            Type.CPU_TIME_SAMPLE,
            Type.WALL_CLOCK_SAMPLE,
            Type.METHOD_TRACE,
            Type.OBJECT_ALLOCATION_SAMPLE,
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB,
            Type.VIRTUAL_THREAD_PINNED);

    private final ProfileInfo primaryInfo;
    private final ProfileInfo secondaryInfo;
    private final ProfileEventTypeRepository primaryEventTypeRepository;
    private final ProfileEventTypeRepository secondaryEventTypeRepository;
    private final DbBasedDiffgraphGenerator generator;
    private final AiExportConfig aiExportConfig;

    public DiffFlamegraphManagerImpl(
            ProfileInfo primaryInfo,
            ProfileInfo secondaryInfo,
            ProfileEventTypeRepository primaryEventTypeRepository,
            ProfileEventTypeRepository secondaryEventTypeRepository,
            DbBasedDiffgraphGenerator generator,
            AiExportConfig aiExportConfig) {

        this.primaryInfo = primaryInfo;
        this.secondaryInfo = secondaryInfo;
        this.primaryEventTypeRepository = primaryEventTypeRepository;
        this.secondaryEventTypeRepository = secondaryEventTypeRepository;
        this.generator = generator;
        this.aiExportConfig = aiExportConfig;
    }

    @Override
    public byte[] generate(GraphParameters parameters) {
        return generator.generate(parameters);
    }

    @Override
    public String generateAiExport(GraphParameters parameters) {
        return generateAiExport(parameters, null);
    }

    @Override
    public String generateAiExport(GraphParameters parameters, AiExportConfig exportConfig) {
        return ProfileComparison.treeMarkdown(
                parameters.eventType(),
                generator.diffFrame(parameters),
                duration(primaryInfo),
                duration(secondaryInfo),
                exportConfig == null ? aiExportConfig : exportConfig);
    }

    @Override
    public String rankedMovements(GraphParameters parameters, int limit) {
        return ProfileComparison.rankedMarkdown(
                parameters.eventType(),
                generator.diffFrame(parameters),
                duration(primaryInfo),
                duration(secondaryInfo),
                limit);
    }

    /**
     * A profile that reports no duration cannot be put on a time base, and
     * {@link cafe.jeffrey.flamegraph.diff.ComparisonScale} says so in its own warning rather than
     * being handed a fabricated one here.
     */
    private static Duration duration(ProfileInfo profileInfo) {
        Duration duration = profileInfo.duration();
        return duration == null ? Duration.ZERO : duration;
    }

    @Override
    public List<EventSummaryResult> eventSummaries() {
        List<EventSummary> primaryEvents = primaryEventTypeRepository.eventSummaries(SUPPORTED_EVENTS).stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .toList();

        List<EventSummary> secondaryEvents = secondaryEventTypeRepository.eventSummaries(SUPPORTED_EVENTS).stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .toList();

        List<EventSummaryResult> results = new ArrayList<>();
        for (EventSummary primary : primaryEvents) {
            Optional<EventSummary> secondaryOpt = findEventType(secondaryEvents, primary.name());
            if (secondaryOpt.isPresent()) {
                EventSummaryResult result = new EventSummaryResult(primary, secondaryOpt.get());
                results.add(result);
            }
        }

        return results;
    }

    @Override
    public List<EventSummaryResult> eventSummaries(SpanScope spanScope) {
        throw new UnsupportedOperationException(
                "Span-scoped event summaries are not supported for differential flamegraphs");
    }

    @Override
    public List<EventSummaryResult> allEventSummaries() {
        List<EventSummary> primaryEvents = primaryEventTypeRepository.eventSummaries().stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .toList();

        List<EventSummary> secondaryEvents = secondaryEventTypeRepository.eventSummaries().stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .toList();

        List<EventSummaryResult> results = new ArrayList<>();
        for (EventSummary primary : primaryEvents) {
            findEventType(secondaryEvents, primary.name())
                    .ifPresent(secondary -> results.add(new EventSummaryResult(primary, secondary)));
        }
        return results;
    }

    private static Optional<EventSummary> findEventType(List<EventSummary> secondary, String eventType) {
        return secondary.stream()
                .filter(e -> eventType.equals(e.name()))
                .findFirst();
    }
}
