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

package pbouda.jeffrey.generator.basic.info;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.event.AllEventsCollector;
import pbouda.jeffrey.generator.basic.event.AllEventsProcessor;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.generator.basic.info.extras.*;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.settings.ActiveSettings;
import pbouda.jeffrey.settings.ActiveSettingsProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class EventInformationProvider implements Supplier<List<EventSummary>> {

    private final ActiveSettingsProvider settingsProvider;
    private final List<Path> recordings;
    private final ProcessableEvents processableEvents;

    public EventInformationProvider(
            ActiveSettingsProvider settingsProvider,
            List<Path> recordings,
            ProcessableEvents processableEvents) {

        this.settingsProvider = settingsProvider;
        this.recordings = recordings;
        this.processableEvents = processableEvents;
    }

    @Override
    public List<EventSummary> get() {
        List<EventSummary> eventSummaries = JdkRecordingIterators.automaticAndCollect(
                recordings,
                () -> new AllEventsProcessor(processableEvents),
                new AllEventsCollector());

        ActiveSettings settings = settingsProvider.get();
        List<ExtraInfoEnhancer> enhancers = List.of(
                new ExecutionSamplesExtraInfo(settings),
                new WallClockSamplesExtraInfo(),
                new TlabAllocationSamplesExtraInfo(settings),
                new ObjectAllocationSamplesExtraInfo(),
                new MonitorEnterExtraInfo(settings),
                new MonitorWaitExtraInfo(),
                new ThreadParkExtraInfo(settings)
        );

        return eventSummaries.stream()
                .map(eventSummary -> applyEnhancers(enhancers, eventSummary))
                .toList();
    }

    private static EventSummary applyEnhancers(List<ExtraInfoEnhancer> enhancers, EventSummary eventSummary) {
        EventSummary current = eventSummary;
        if (enhancers != null) {
            for (ExtraInfoEnhancer enhancer : enhancers) {
                if (enhancer.isApplicable(Type.fromCode(current.name()))) {
                    current = enhancer.apply(current);
                }
            }
        }
        return current;
    }
}
