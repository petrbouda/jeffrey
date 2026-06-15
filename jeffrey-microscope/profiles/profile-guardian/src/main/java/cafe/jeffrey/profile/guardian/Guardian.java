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

package cafe.jeffrey.profile.guardian;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.frameir.RecordsFrameIterator;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.shared.common.model.EventSourceResolver;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.settings.ActiveSetting;
import cafe.jeffrey.shared.common.settings.ActiveSettings;
import cafe.jeffrey.profile.guardian.definition.GuardDefinition;
import cafe.jeffrey.profile.guardian.definition.GuardDefinitions;
import cafe.jeffrey.profile.guardian.guard.ConfigurableGuard;
import cafe.jeffrey.profile.guardian.guard.Guard;
import cafe.jeffrey.profile.guardian.preconditions.GuardianInformation;
import cafe.jeffrey.profile.guardian.preconditions.GuardianInformationBuilder;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;
import cafe.jeffrey.profile.guardian.preconditions.PreconditionsBuilder;
import cafe.jeffrey.profile.guardian.prereq.PrerequisitesEvaluator;
import cafe.jeffrey.profile.guardian.traverse.FrameTraversal;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Guardian {

    private static final Logger LOG = LoggerFactory.getLogger(Guardian.class);

    private static final String PREREQUISITES_GROUP = "Prerequisites";

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;
    private final ProfileEventTypeRepository eventTypeRepository;
    private final ActiveSettings activeSettings;
    private final GuardDefinitions definitions;

    public Guardian(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository,
            ProfileEventTypeRepository eventTypeRepository,
            ActiveSettings activeSettings,
            GuardDefinitions definitions) {

        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.activeSettings = activeSettings;
        this.definitions = definitions;
    }

    public List<GuardianResult> process() {
        GuardianInformation recordingInfo = buildGuardianInformation(activeSettings);

        List<EventSummary> eventSummaries = eventTypeRepository.eventSummaries();

        Map<String, Long> samplesByEventType = new HashMap<>();
        for (EventSummary summary : eventSummaries) {
            samplesByEventType.merge(summary.name(), summary.samples(), Long::sum);
        }

        // The recording is async-profiler if it carries any event from the `profiler.` namespace — the
        // same rule the parser uses to set the recording source. Derived from the event summaries Guardian
        // already loaded so JVM-internal guards (JIT, Safepoint, ...) aren't wrongly Not Applicable.
        RecordingEventSource eventSource = EventSourceResolver.fromEventTypeNames(
                eventSummaries.stream().map(EventSummary::name).toList());

        Preconditions preconditions = new PreconditionsBuilder()
                .withEventTypes(eventSummaries)
                .withEventSource(eventSource)
                .withDebugSymbolsAvailable(recordingInfo.debugSymbolsAvailable())
                .withKernelSymbolsAvailable(recordingInfo.kernelSymbolsAvailable())
                .withGarbageCollectorType(recordingInfo.garbageCollectorType())
                .build();

        List<GuardianResult> results = new ArrayList<>();

        // Prerequisites (data-quality) checks run unconditionally and populate the UI's
        // "Prerequisites" panel. They do not require event data and have no flamegraph.
        for (GuardianResult prereq : PrerequisitesEvaluator.evaluate(profileInfo, preconditions)) {
            results.add(new GuardianResult(prereq.analysisItem().withGroup(PREREQUISITES_GROUP), prereq.frame()));
        }

        // Guards are grouped by the JFR event type whose stacktraces they analyse. For each event type
        // present in the recording we build a single (weighted) frame tree and run all of its guards
        // over it; a guard reads samples or weight from the tree according to its result type.
        Map<String, List<GuardDefinition>> byEventType = definitions.all().stream()
                .collect(Collectors.groupingBy(GuardDefinition::eventType, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<GuardDefinition>> entry : byEventType.entrySet()) {
            results.addAll(evaluateEventType(entry.getKey(), entry.getValue(), samplesByEventType, preconditions));
        }

        return results;
    }

    private List<GuardianResult> evaluateEventType(
            String eventType,
            List<GuardDefinition> eventTypeDefinitions,
            Map<String, Long> samplesByEventType,
            Preconditions preconditions) {

        Guard.ProfileInfo guardProfileInfo = new Guard.ProfileInfo(profileInfo.id(), Type.fromCode(eventType));
        List<ConfigurableGuard> guards = eventTypeDefinitions.stream()
                .map(definition -> new ConfigurableGuard(guardProfileInfo, definition))
                .toList();

        long availableSamples = samplesByEventType.getOrDefault(eventType, 0L);
        long requiredSamples = eventTypeDefinitions.stream()
                .mapToLong(GuardDefinition::minSamples)
                .min()
                .orElse(0L);

        // Only build the (expensive) frame tree when the event type is present with enough samples;
        // otherwise every guard for this event type reports Not Applicable via its un-started state.
        if (samplesByEventType.containsKey(eventType) && availableSamples >= requiredSamples) {
            List<ConfigurableGuard> applicable = guards.stream()
                    .filter(guard -> guard.initialize(preconditions))
                    .toList();

            if (!applicable.isEmpty()) {
                GraphParameters params = GraphParameters.builder()
                        .withEventType(Type.fromCode(eventType))
                        .withUseWeight(true)
                        .build();
                Frame frame = new RecordsFrameIterator(params, eventStreamRepository).iterate();
                new FrameTraversal(frame).traverseWith(applicable);
            }
        }

        List<GuardianResult> results = new ArrayList<>();
        for (ConfigurableGuard guard : guards) {
            GuardianResult result = guard.result();
            results.add(new GuardianResult(result.analysisItem().withGroup(eventType), result.frame()));
        }
        return results;
    }

    private GuardianInformation buildGuardianInformation(ActiveSettings activeSettings) {
        GuardianInformationBuilder builder = new GuardianInformationBuilder();

        Optional<ActiveSetting> activeRecordingOpt = activeSettings.findFirstByType(Type.ACTIVE_RECORDING);
        if (activeRecordingOpt.isPresent()) {
            ActiveSetting activeRecording = activeRecordingOpt.get();

            activeRecording.getParam("debugSymbols")
                    .ifPresent(value -> builder.setDebugSymbolsAvailable(Boolean.parseBoolean(value)));

            activeRecording.getParam("kernelSymbols")
                    .ifPresent(value -> builder.setKernelSymbolsAvailable(Boolean.parseBoolean(value)));
        }

        List<JsonNode> gcConfigurationFields = eventRepository.eventsByTypeWithFields(Type.GC_CONFIGURATION);
        if (!gcConfigurationFields.isEmpty()) {
            JsonNode gcConfiguration = gcConfigurationFields.getFirst();

            String oldCollector = gcConfiguration.get("oldCollector").asString();
            GarbageCollectorType oldGC = GarbageCollectorType.fromOldGenCollector(oldCollector);
            if (oldGC == null) {
                LOG.warn("Unknown Old Generation Garbage Collector: {}", oldCollector);
            } else {
                builder.setGarbageCollectorType(oldGC);
            }
        }

        return builder.build();
    }
}
