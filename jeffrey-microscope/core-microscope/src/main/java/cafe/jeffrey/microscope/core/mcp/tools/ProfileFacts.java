/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSections.SectionAvailability;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.mcp.finding.McpFinding;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What {@code profiles_summary} already knows about a profile, gathered once so the investigation menu
 * decides every area from the same facts and reads nothing the summary did not.
 *
 * @param profileId          the profile
 * @param eventSource        what the profile was made from
 * @param disabledFeatures   the features the profile has no data for
 * @param recordedEventTypes the event type codes with at least one sample
 * @param panels             every flamegraph panel the profile offers, recorded or not, in panel order
 * @param jvmSections        every jvm_ section with whether its events were recorded
 * @param autoAnalysis       whether the rules ran
 * @param flagged            the rules that flagged something, most severe first
 */
record ProfileFacts(
        String profileId,
        RecordingEventSource eventSource,
        Set<FeatureType> disabledFeatures,
        Set<String> recordedEventTypes,
        List<Sampled> panels,
        List<SectionAvailability> jvmSections,
        AutoAnalysisStatus autoAnalysis,
        List<McpFinding> flagged) {

    /**
     * One flamegraph panel: what it measures, its event type, and how many samples it holds (zero for
     * a panel the profile did not record).
     */
    record Sampled(SampleKind kind, String eventType, long samples) {

        boolean recorded() {
            return samples > 0;
        }
    }

    ProfileFacts {
        disabledFeatures = Set.copyOf(disabledFeatures);
        recordedEventTypes = Set.copyOf(recordedEventTypes);
        panels = List.copyOf(panels);
        jvmSections = List.copyOf(jvmSections);
        flagged = List.copyOf(flagged);
    }

    boolean enabled(FeatureType feature) {
        return !disabledFeatures.contains(feature);
    }

    boolean recordsAny(Set<String> eventTypes) {
        return eventTypes.stream().anyMatch(recordedEventTypes::contains);
    }

    boolean jvmSectionAvailable(String id) {
        return jvmSections.stream().anyMatch(section -> section.id().equals(id) && section.available());
    }

    /** The first recorded panel of the kind, in panel order — the event type to graph it with. */
    Optional<Sampled> recorded(SampleKind kind) {
        return panels.stream().filter(panel -> panel.kind() == kind && panel.recorded()).findFirst();
    }

    /** The panels of the kind the profile did not record, for saying what is missing. */
    List<Sampled> unrecorded(SampleKind kind) {
        return panels.stream().filter(panel -> panel.kind() == kind && !panel.recorded()).toList();
    }

    /** The recorded panel with the most samples, the one a timeline is drawn from. */
    Optional<Sampled> busiest() {
        return panels.stream()
                .filter(Sampled::recorded)
                .max(Comparator.comparingLong(Sampled::samples));
    }
}
