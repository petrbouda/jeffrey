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

import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReport;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSections.SectionAvailability;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.mcp.finding.McpFinding;

import java.util.EnumSet;
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
 * @param heap               how far a heap dump is prepared; {@link HeapState#NOT_A_HEAP_DUMP} for any other profile
 */
record ProfileFacts(
        String profileId,
        RecordingEventSource eventSource,
        Set<FeatureType> disabledFeatures,
        Set<String> recordedEventTypes,
        List<Sampled> panels,
        List<SectionAvailability> jvmSections,
        AutoAnalysisStatus autoAnalysis,
        List<McpFinding> flagged,
        HeapState heap) {

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
        if (heap == null) {
            throw new IllegalArgumentException("a profile's heap state is NOT_A_HEAP_DUMP rather than null");
        }
    }

    /**
     * How far a heap dump is prepared: whether its file is still there, whether its index is built, and
     * which of the reports {@code heap_prepare} computes on their own are stored. Each is a look at what
     * is on disk, never a computation.
     *
     * @param present  whether the profile still has its copy of the dump
     * @param indexed  whether the index every heap_ reader needs is built
     * @param computed the stored reports
     */
    record HeapState(boolean present, boolean indexed, Set<HeapReport> computed) {

        static final HeapState NOT_A_HEAP_DUMP = new HeapState(false, false, Set.of());

        HeapState {
            computed = computed.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(computed));
            if (indexed && !present) {
                throw new IllegalArgumentException("a heap dump without its file has no index to read");
            }
        }

        /** What is on disk for one heap dump profile. */
        static HeapState of(HeapDumpManager manager) {
            if (!manager.heapDumpExists()) {
                return new HeapState(false, false, Set.of());
            }
            if (!manager.isCacheReady()) {
                return new HeapState(true, false, Set.of());
            }
            return new HeapState(true, true, HeapReport.storedIn(manager));
        }

        /** Whether a reader that needs the report — or only the index, for none — can answer now. */
        boolean ready(HeapReport report) {
            return indexed && (report == null || computed.contains(report));
        }
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
}
