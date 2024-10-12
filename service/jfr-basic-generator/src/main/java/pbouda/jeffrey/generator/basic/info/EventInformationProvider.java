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

import pbouda.jeffrey.common.Recording;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.event.AllEventsCollector;
import pbouda.jeffrey.generator.basic.event.AllEventsProcessor;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class EventInformationProvider implements Supplier<List<EventSummary>> {

    private final List<Path> recordings;
    private final CompositeExtraInfoEnhancer extraInfoEnhancer = new CompositeExtraInfoEnhancer();
    private final ProcessableEvents processableEvents;
    private final boolean enhanceEventTypeInfo;

    public EventInformationProvider(List<Path> recordings) {
        this(recordings, ProcessableEvents.all(), true);
    }

    public EventInformationProvider(List<Path> recordings, boolean enhanceEventTypeInfo) {
        this(recordings, ProcessableEvents.all(), enhanceEventTypeInfo);
    }

    public EventInformationProvider(
            List<Path> recordings, ProcessableEvents processableEvents, boolean enhanceEventTypeInfo) {

        this.recordings = recordings;
        this.processableEvents = processableEvents;
        this.enhanceEventTypeInfo = enhanceEventTypeInfo;
    }

    public static EventInformationProvider ofRecordings(List<Recording> recordings) {
        return new EventInformationProvider(recordingsToPaths(recordings), ProcessableEvents.all(), true);
    }

    public static EventInformationProvider ofRecordings(
            List<Recording> recordings, List<Type> supportedEvents) {

        return new EventInformationProvider(
                recordingsToPaths(recordings), new ProcessableEvents(supportedEvents), true);
    }

    public static EventInformationProvider ofRecordings(
            List<Recording> recordings, List<Type> supportedEvents, boolean enhanceEventTypeInfo) {

        return new EventInformationProvider(
                recordingsToPaths(recordings), new ProcessableEvents(supportedEvents), enhanceEventTypeInfo);
    }

    private static List<Path> recordingsToPaths(List<Recording> recordings) {
        return recordings.stream().map(Recording::absolutePath).toList();
    }

    @Override
    public List<EventSummary> get() {
        List<EventSummary> eventSummaries = JdkRecordingIterators.automaticAndCollect(
                recordings,
                () -> new AllEventsProcessor(processableEvents),
                new AllEventsCollector());

        if (enhanceEventTypeInfo) {
            this.extraInfoEnhancer.initialize(recordings);
            return eventSummaries.stream()
                    .map(extraInfoEnhancer)
                    .toList();
        } else {
            return eventSummaries;
        }
    }
}
