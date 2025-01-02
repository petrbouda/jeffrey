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

import pbouda.jeffrey.calculated.nativeleak.summary.NativeLeakEventSummaryCalculator;
import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.profile.settings.ActiveSettings;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.summary.enhancer.*;
import pbouda.jeffrey.profile.summary.event.AllEventsCollector;
import pbouda.jeffrey.profile.summary.event.AllEventsProcessor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MINUTES;

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

        CompletableFuture<List<EventSummary>> realEventsFuture = CompletableFuture.supplyAsync(() -> {
            return JdkRecordingIterators.automaticAndCollect(
                    recordings,
                    () -> new AllEventsProcessor(processableEvents),
                    new AllEventsCollector());
        });

        CompletableFuture<List<EventSummary>> calculatedEventsFuture = CompletableFuture.supplyAsync(() -> {
            return Stream.of(new NativeLeakEventSummaryCalculator(recordings))
                    .map(NativeLeakEventSummaryCalculator::calculate)
                    // TODO: find the better way how to filter out the empty events
                    //  (or stop calculate events that are not included in the recordings)
                    .filter(eventSummary -> eventSummary.samples() > 0)
                    .toList();
        });

        List<EventSummary> eventSummaries = realEventsFuture
                .thenCombine(calculatedEventsFuture, (realEvents, calculatedEvents) -> {
                    List<EventSummary> joinedEventSummaries = new ArrayList<>();
                    joinedEventSummaries.addAll(realEvents);
                    joinedEventSummaries.addAll(calculatedEvents);
                    return joinedEventSummaries;
                })
                // The timeout is set to 15 minutes because the processing of the events can take a long time
                .orTimeout(15, MINUTES)
                .join();

        List<EventSummaryEnhancer> enhancers = List.of(
                new ExecutionSamplesExtraEnhancer(settings),
                new WallClockSamplesExtraEnhancer(),
                new NativeMallocAllocationSamplesExtraEnhancer(),
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
