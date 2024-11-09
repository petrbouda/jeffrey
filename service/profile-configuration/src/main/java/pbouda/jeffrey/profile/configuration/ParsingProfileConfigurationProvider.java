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

package pbouda.jeffrey.profile.configuration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.RecordingUtils;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ParsingProfileConfigurationProvider implements ProfileConfigurationProvider {

    private record RecordingCandidate(Path recording, List<Type> eventTypes) {
    }

    private static final List<Type> EVENT_TYPES = List.of(
            Type.JVM_INFORMATION,
            Type.CONTAINER_CONFIGURATION,
            Type.CPU_INFORMATION,
            Type.OS_INFORMATION,
            Type.GC_CONFIGURATION,
            Type.GC_HEAP_CONFIGURATION,
            Type.GC_SURVIVOR_CONFIGURATION,
            Type.GC_TLAB_CONFIGURATION,
            Type.YOUNG_GENERATION_CONFIGURATION,
            Type.COMPILER_CONFIGURATION,
            Type.VIRTUALIZATION_INFORMATION
    );

    private final List<Path> recordings;

    public ParsingProfileConfigurationProvider(List<Path> recordings) {
        this.recordings = recordings.stream()
                .sorted()
                .toList();
    }

    @Override
    public ObjectNode get() {
        ObjectNode result = Json.createObject();

        // What configuration contains every recording file
        List<RecordingCandidate> candidates = selectRecordingCandidates(recordings);

        for (RecordingCandidate candidate : candidates) {
            List<ConfigurationEvent> configurations = JdkRecordingIterators.singleAndCollectPartial(
                    candidate.recording, new JsonFieldEventProcessor(candidate.eventTypes));

            for (ConfigurationEvent configuration : configurations) {
                result.set(configuration.eventType().getLabel(), configuration.content());
            }
        }

        return result;
    }

    private static List<RecordingCandidate> selectRecordingCandidates(List<Path> recordings) {
        Set<Type> remainingTypes = new HashSet<>(EVENT_TYPES);
        List<RecordingCandidate> candidates = new ArrayList<>();
        for (Path recording : recordings) {
            List<EventType> eventTypes = RecordingUtils.listEventTypes(recording);

            // Available event types in the recording
            List<Type> availableInRecording = eventTypes.stream()
                    .map(Type::from)
                    .filter(EVENT_TYPES::contains)
                    .toList();

            candidates.add(new RecordingCandidate(recording, availableInRecording));

            // Remove the types that are already in the recording to track what events are missing
            availableInRecording.forEach(remainingTypes::remove);

            // Break the loop if we covered all the expected events
            if (remainingTypes.isEmpty()) {
                break;
            }
        }
        return candidates;
    }
}
