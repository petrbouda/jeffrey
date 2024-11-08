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

package pbouda.jeffrey.guardian;

import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.guardian.preconditions.*;
import pbouda.jeffrey.guardian.type.AllocationGuardianGroup;
import pbouda.jeffrey.guardian.type.ExecutionSampleGuardianGroup;
import pbouda.jeffrey.guardian.type.GuardianGroup;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.profile.summary.ParsingEventSummaryProvider;
import pbouda.jeffrey.profile.summary.event.EventSummary;
import pbouda.jeffrey.profile.settings.ActiveSettings;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class Guardian {

    private final ActiveSettingsProvider settingsProvider;

    public Guardian(ActiveSettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
    }

    public List<GuardianResult> process(Config config) {
        long start = System.nanoTime();

        GuardRecordingInformation recordingInfo = JdkRecordingIterators.automaticAndCollectPartial(
                config.primaryRecordings(),
                GuardRecordingInformationEventProcessor::new,
                new PreconditionsCollector());

        long recordingInfoTimestamp = System.nanoTime();

        List<EventSummary> eventSummaries = new ParsingEventSummaryProvider(
                settingsProvider, config.primaryRecordings(), ProcessableEvents.all()).get();

        long eventInfoTimestamp = System.nanoTime();

        Preconditions preconditions = new PreconditionsBuilder()
                .withEventTypes(eventSummaries)
                .withEventSource(recordingInfo.getEventSource())
                .withDebugSymbolsAvailable(recordingInfo.getDebugSymbolsAvailable())
                .withKernelSymbolsAvailable(recordingInfo.getKernelSymbolsAvailable())
                .withGarbageCollectorType(recordingInfo.getGarbageCollectorType())
                .build();

        ActiveSettings activeSettings = settingsProvider.get();
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

        long guardiansTimestamp = System.nanoTime();

        System.out.println(
                "Frame: " + Duration.ofNanos(recordingInfoTimestamp - start).toMillis() +
                        "\n Event Info: " + Duration.ofNanos(eventInfoTimestamp - recordingInfoTimestamp).toMillis() +
                        "\n Guardian: " + Duration.ofNanos(guardiansTimestamp - eventInfoTimestamp).toMillis() +
                        "\n Total: " + Duration.ofNanos(guardiansTimestamp - start).toMillis()
        );

        return results;
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
