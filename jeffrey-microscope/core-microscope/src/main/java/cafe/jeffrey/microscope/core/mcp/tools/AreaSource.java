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

import cafe.jeffrey.microscope.core.mcp.tools.ProfileCapabilityGaps.CapabilityGap;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSections.SectionAvailability;
import cafe.jeffrey.profile.feature.FeatureType;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Where an investigation area's data comes from, and so whether this profile can answer it: one kind
 * of availability per implementation, each reading what {@link ProfileFacts} already holds.
 */
sealed interface AreaSource {

    String LIST_SEPARATOR = ", ";

    /** Whether the profile holds what the area reads. */
    boolean available(ProfileFacts facts);

    /** What is missing, in one sentence, for an area that is not available. */
    String gap(ProfileFacts facts);

    /** What would capture it next time; null when nothing short of different instrumentation would. */
    String remedy();

    /**
     * Stack samples of some kinds — a flamegraph group.
     *
     * @param kinds  the kinds the area reads, any of which is enough
     * @param remedy the profiler option that records them
     */
    record Samples(Set<SampleKind> kinds, String remedy) implements AreaSource {

        private static final String GAP = "The recording holds no %s samples, so this cannot be assessed from it.";
        private static final String ANY_SAMPLES = "such";

        public Samples {
            kinds = Set.copyOf(kinds);
        }

        @Override
        public boolean available(ProfileFacts facts) {
            return kinds.stream().anyMatch(kind -> facts.recorded(kind).isPresent());
        }

        @Override
        public String gap(ProfileFacts facts) {
            List<String> missing = kinds.stream()
                    .flatMap(kind -> facts.unrecorded(kind).stream())
                    .map(ProfileFacts.Sampled::eventType)
                    .sorted()
                    .toList();
            return GAP.formatted(missing.isEmpty() ? ANY_SAMPLES : String.join(LIST_SEPARATOR, missing));
        }
    }

    /**
     * A jvm_ dashboard, available when any of the sections has its events.
     *
     * @param ids the section ids, as {@code jvm_sections} names them
     */
    record JvmSectionsOf(Set<String> ids) implements AreaSource {

        private static final String GAP = "No %s events were recorded, so %s has nothing to render.";
        private static final String REMEDY = "Enable those event types in the recording settings for the next run.";

        public JvmSectionsOf {
            ids = Set.copyOf(ids);
        }

        @Override
        public boolean available(ProfileFacts facts) {
            return sections(facts).stream().anyMatch(SectionAvailability::available);
        }

        @Override
        public String gap(ProfileFacts facts) {
            List<SectionAvailability> sections = sections(facts);
            return GAP.formatted(
                    sections.stream().flatMap(section -> section.eventTypes().stream())
                            .distinct().sorted().collect(Collectors.joining(LIST_SEPARATOR)),
                    sections.stream().map(SectionAvailability::tool)
                            .distinct().collect(Collectors.joining(LIST_SEPARATOR)));
        }

        @Override
        public String remedy() {
            return REMEDY;
        }

        private List<SectionAvailability> sections(ProfileFacts facts) {
            return facts.jvmSections().stream().filter(section -> ids.contains(section.id())).toList();
        }
    }

    /**
     * Data a profile feature stands for — traces, the HTTP or JDBC dashboards — available when any of
     * the features is; the gap is the capability gap's own sentence for the first.
     *
     * @param anyOf the features, in the order their gap is preferred
     */
    record Features(List<FeatureType> anyOf) implements AreaSource {

        public Features {
            if (anyOf.isEmpty()) {
                throw new IllegalArgumentException("a feature source needs at least one feature");
            }
            anyOf = List.copyOf(anyOf);
        }

        @Override
        public boolean available(ProfileFacts facts) {
            return anyOf.stream().anyMatch(facts::enabled);
        }

        @Override
        public String gap(ProfileFacts facts) {
            return first().gap();
        }

        @Override
        public String remedy() {
            return first().remedy();
        }

        private CapabilityGap first() {
            return ProfileCapabilityGaps.featureGap(anyOf.getFirst());
        }
    }

    /**
     * Events of named types, available when any was recorded.
     *
     * @param anyOf  the event type codes
     * @param remedy what records them
     */
    record EventTypes(Set<String> anyOf, String remedy) implements AreaSource {

        private static final String GAP = "No %s events were recorded, so this cannot be assessed from it.";

        public EventTypes {
            anyOf = Set.copyOf(anyOf);
        }

        @Override
        public boolean available(ProfileFacts facts) {
            return anyOf.stream().anyMatch(facts.recordedEventTypes()::contains);
        }

        @Override
        public String gap(ProfileFacts facts) {
            return GAP.formatted(sorted(anyOf));
        }
    }

    /** The auto-analysis rules, available unless they cannot run on this profile at all. */
    record Rules() implements AreaSource {

        @Override
        public boolean available(ProfileFacts facts) {
            return facts.autoAnalysis() != AutoAnalysisStatus.CANNOT_COMPUTE;
        }

        @Override
        public String gap(ProfileFacts facts) {
            return ProfileCapabilityGaps.AUTO_ANALYSIS_IMPOSSIBLE_REMEDY;
        }

        @Override
        public String remedy() {
            return null;
        }
    }

    /** Samples over time: per-sample timestamps and at least one recorded panel to draw. */
    record Timeline() implements AreaSource {

        @Override
        public boolean available(ProfileFacts facts) {
            return facts.enabled(FeatureType.TIMESERIES) && facts.busiest().isPresent();
        }

        @Override
        public String gap(ProfileFacts facts) {
            return ProfileCapabilityGaps.featureGap(FeatureType.TIMESERIES).gap();
        }

        @Override
        public String remedy() {
            return ProfileCapabilityGaps.featureGap(FeatureType.TIMESERIES).remedy();
        }
    }

    private static String sorted(Collection<String> values) {
        return values.stream().sorted().collect(Collectors.joining(LIST_SEPARATOR));
    }
}
