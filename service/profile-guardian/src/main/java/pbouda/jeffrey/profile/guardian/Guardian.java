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

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.GarbageCollectorType;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.model.ActiveSetting;
import pbouda.jeffrey.common.model.ActiveSettings;
import pbouda.jeffrey.profile.guardian.preconditions.GuardianInformation;
import pbouda.jeffrey.profile.guardian.preconditions.GuardianInformationBuilder;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.preconditions.PreconditionsBuilder;
import pbouda.jeffrey.profile.guardian.type.AllocationGuardianGroup;
import pbouda.jeffrey.profile.guardian.type.ExecutionSampleGuardianGroup;
import pbouda.jeffrey.profile.guardian.type.GuardianGroup;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Guardian {

    private static final Logger LOG = LoggerFactory.getLogger(Guardian.class);

    private final ProfileEventRepository eventRepository;
    private final ProfileEventTypeRepository eventTypeRepository;
    private final ActiveSettings activeSettings;

    public Guardian(
            ProfileEventRepository eventRepository,
            ProfileEventTypeRepository eventTypeRepository,
            ActiveSettings activeSettings) {

        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.activeSettings = activeSettings;
    }

    public List<GuardianResult> process(Config config) {
        GuardianInformation recordingInfo = buildGuardianInformation(activeSettings);

        List<EventSummary> eventSummaries = eventTypeRepository.eventSummaries();

        Preconditions preconditions = new PreconditionsBuilder()
                .withEventTypes(eventSummaries)
                .withEventSource(recordingInfo.eventSource())
                .withDebugSymbolsAvailable(recordingInfo.debugSymbolsAvailable())
                .withKernelSymbolsAvailable(recordingInfo.kernelSymbolsAvailable())
                .withGarbageCollectorType(recordingInfo.garbageCollectorType())
                .build();

        List<GuardianGroup> groups = List.of(
                new ExecutionSampleGuardianGroup(eventRepository, activeSettings, 1000),
                new AllocationGuardianGroup(eventRepository, activeSettings, 1000)
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

    private GuardianInformation buildGuardianInformation(ActiveSettings activeSettings) {
        GuardianInformationBuilder builder = new GuardianInformationBuilder();

        Optional<ActiveSetting> activeRecordingOpt = activeSettings.findFirstByType(Type.ACTIVE_RECORDING);
        if (activeRecordingOpt.isPresent()) {
            ActiveSetting activeRecording = activeRecordingOpt.get();
            // param `features` is available in Async-profiler recordings
            EventSource source = activeRecording.getParam("features")
                    .map(f -> EventSource.ASYNC_PROFILER)
                    .orElse(EventSource.JDK);

            builder.setEventSource(source);

            activeRecording.getParam("debugSymbols")
                    .ifPresent(value -> builder.setDebugSymbolsAvailable(Boolean.parseBoolean(value)));

            activeRecording.getParam("kernelSymbols")
                    .ifPresent(value -> builder.setKernelSymbolsAvailable(Boolean.parseBoolean(value)));
        }

        List<JsonNode> gcConfigurationFields = eventRepository.eventsByTypeWithFields(Type.GC_CONFIGURATION);
        if (gcConfigurationFields.size() > 1) {
            JsonNode gcConfiguration = gcConfigurationFields.getFirst();

            String oldCollector = gcConfiguration.get("oldCollector").asText();
            GarbageCollectorType oldGC = GarbageCollectorType.fromOldGenCollector(oldCollector);
            if (oldGC == null) {
                LOG.warn("Unknown Old Generation Garbage Collector: {}", oldCollector);
            } else {
                builder.setGarbageCollectorType(oldGC);
            }
        }

        return builder.build();
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
