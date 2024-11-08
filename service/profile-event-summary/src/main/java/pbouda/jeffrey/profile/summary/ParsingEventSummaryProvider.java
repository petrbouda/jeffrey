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

package pbouda.jeffrey.profile.summary;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.profile.settings.ActiveSettings;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.summary.enhancer.*;
import pbouda.jeffrey.profile.summary.event.AllEventsCollector;
import pbouda.jeffrey.profile.summary.event.AllEventsProcessor;
import pbouda.jeffrey.profile.summary.event.EventSummary;

import java.nio.file.Path;
import java.util.List;

public class ParsingEventSummaryProvider implements EventSummaryProvider {

    private final ActiveSettingsProvider settingsProvider;
    private final List<Path> recordings;
    private final ProcessableEvents processableEvents;

    public ParsingEventSummaryProvider(ActiveSettingsProvider settingsProvider, List<Path> recordings) {
        this(settingsProvider, recordings, ProcessableEvents.all());
    }

    public ParsingEventSummaryProvider(
            ActiveSettingsProvider settingsProvider,
            List<Path> recordings,
            ProcessableEvents processableEvents) {

        this.settingsProvider = settingsProvider;
        this.recordings = recordings;
        this.processableEvents = processableEvents;
    }

    @Override
    public List<EventSummary> get() {
        ActiveSettings settings = settingsProvider.get();

        List<EventSummary> eventSummaries = JdkRecordingIterators.automaticAndCollect(
                recordings,
                () -> new AllEventsProcessor(processableEvents),
                new AllEventsCollector());

        List<EventSummaryEnhancer> enhancers = List.of(
                new ExecutionSamplesExtraEnhancer(settings),
                new WallClockSamplesExtraEnhancer(),
                new TlabAllocationSamplesExtraEnhancer(settings),
                new ObjectAllocationSamplesExtraEnhancer(),
                new MonitorEnterExtraEnhancer(settings),
                new MonitorWaitExtraEnhancer(),
                new ThreadParkExtraEnhancer(settings),
                new WallClockSamplesWeightEnhancer(settings),
                new ExecutionSamplesWeightEnhancer(settings)
        );

        return eventSummaries.stream()
                .map(eventSummary -> applyEnhancers(enhancers, eventSummary))
                .toList();
    }

    private static EventSummary applyEnhancers(List<EventSummaryEnhancer> enhancers, EventSummary eventSummary) {
        EventSummary current = eventSummary;
        if (enhancers != null) {
            for (EventSummaryEnhancer enhancer : enhancers) {
                if (enhancer.isApplicable(Type.fromCode(current.name()))) {
                    current = enhancer.apply(current);
                }
            }
        }
        return current;
    }
}
