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

package pbouda.jeffrey.profile.guardian;

import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.persistence.profile.EventsReadRepository;
import pbouda.jeffrey.profile.guardian.preconditions.*;
import pbouda.jeffrey.profile.guardian.type.AllocationGuardianGroup;
import pbouda.jeffrey.profile.guardian.type.ExecutionSampleGuardianGroup;
import pbouda.jeffrey.profile.guardian.type.GuardianGroup;
import pbouda.jeffrey.profile.settings.ActiveSettings;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;

import java.util.ArrayList;
import java.util.List;

public class Guardian {

    private final EventsReadRepository eventsReadRepository;
    private final ActiveSettingsProvider settingsProvider;

    public Guardian(EventsReadRepository eventsReadRepository, ActiveSettingsProvider settingsProvider) {
        this.eventsReadRepository = eventsReadRepository;
        this.settingsProvider = settingsProvider;
    }

    public List<GuardianResult> process(Config config) {
        ActiveSettings activeSettings = settingsProvider.get();

//        GuardianInformation recordingInfo = buildGuardianInformation(activeSettings, eventsReadRepository);

        GuardianInformation recordingInfo = JdkRecordingIterators.automaticAndCollect(
                config.primaryRecordings(),
                GuardRecordingInformationEventProcessor::new,
                new PreconditionsCollector());

        List<EventSummary> eventSummaries = eventsReadRepository.eventSummaries();

        Preconditions preconditions = new PreconditionsBuilder()
                .withEventTypes(eventSummaries)
                .withEventSource(recordingInfo.eventSource())
                .withDebugSymbolsAvailable(recordingInfo.debugSymbolsAvailable())
                .withKernelSymbolsAvailable(recordingInfo.kernelSymbolsAvailable())
                .withGarbageCollectorType(recordingInfo.garbageCollectorType())
                .build();

        List<GuardianGroup> groups = List.of(
                new ExecutionSampleGuardianGroup(activeSettings, 1000),
                new AllocationGuardianGroup(activeSettings, 1000)
        );

        List<GuardianResult> results = new ArrayList<>();
        for (GuardianGroup group : groups) {
            EventSummary eventSummary = selectEventSummary(group, eventSummaries);

            if (eventSummary != null) {
                Type eventType = Type.fromCode(eventSummary.name());
                List<GuardianResult> groupResults = group.execute(
                        config.copyWithType(eventType), eventSummary, preconditions);
                results.addAll(groupResults);
            }
        }

        return results;
    }

    private static GuardianInformation buildGuardianInformation(
            ActiveSettings activeSettings, EventsReadRepository eventsReadRepository) {

        return null;
    }

    private static EventSummary selectEventSummary(GuardianGroup group, List<EventSummary> eventSummaries) {
        for (EventSummary eventSummary : eventSummaries) {
            if (group.applicableTypes().contains(Type.fromCode(eventSummary.name()))) {
                return eventSummary;
            }
        }
        return null;
    }
}
