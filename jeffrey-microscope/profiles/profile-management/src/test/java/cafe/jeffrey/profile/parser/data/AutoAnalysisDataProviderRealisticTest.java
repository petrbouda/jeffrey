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

package cafe.jeffrey.profile.parser.data;

import cafe.jeffrey.jfrparser.raw.JfrParser;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.test.recording.PersonsRecording;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openjdk.jmc.flightrecorder.rules.RuleRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static cafe.jeffrey.test.recording.PersonsRecording.DIRECT;
import static cafe.jeffrey.test.recording.PersonsRecording.DOM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JMC rules over realistic recordings: two runs of one service under load, which the rules
 * judge — context switches, errors thrown, the GC's parallelism, competing processes, long socket
 * reads. What they find is held to what each run holds, however the run is handed to them.
 *
 * <p>One part of it is not stable, and it is named rather than hidden. Each file is a {@code jfrsync}
 * recording: the JDK's chain and async-profiler's both define {@code jdk.ActiveSetting} and
 * {@code jdk.ActiveRecording}, with different fields. JMC keeps one registry of event types for the
 * whole JVM and files the definition that does not match under a renamed type
 * ({@code jdk.ActiveSetting<uuid>}); which chain keeps the name depends on what an earlier load left
 * in the registry. A rule that reads the recording's settings — whether an event type was on, at
 * what period — therefore sees one chain's settings on one evaluation and the other's on the next.
 * Those rules are {@link #SETTINGS_DEPENDENT}; every other finding is the same every time.
 */
@DisplayName("Auto-analysis over realistic recordings")
class AutoAnalysisDataProviderRealisticTest {

    /**
     * The rules whose result depends on which chain's {@code jdk.ActiveSetting} JMC kept under its
     * name on a given load: they differ between two evaluations of one file in one JVM.
     */
    private static final Set<String> SETTINGS_DEPENDENT = Set.of(
            "Method Profiling", "Allocated Classes", "Threads Allocating", "TLAB Allocation Ratio",
            "GC Freed Ratio", "GC Pressure");

    /** What each run is warned about. */
    private static final Map<PersonsRecording, Set<String>> WARNINGS = new EnumMap<>(Map.of(
            DIRECT, Set.of("Competing CPU Ratio Usage", "Context Switches", "Thrown Errors", "Inverted Parallelism"),
            DOM, Set.of("Context Switches", "Thrown Errors", "Inverted Parallelism")));

    /** What each run is told, beside its warnings, that holds every time. */
    private static final Map<PersonsRecording, Set<String>> STABLE_INFO = new EnumMap<>(Map.of(
            DIRECT, Set.of("Competing Processes", "Process Started", "Socket Read Peak Duration"),
            DOM, Set.of("Competing Processes", "Process Started", "Socket Read Peak Duration")));

    /** How often the GC's parallelism inverted, of how many events: the figure the warning reports. */
    private static final Map<PersonsRecording, String> INVERTED_PARALLELISM = new EnumMap<>(Map.of(
            DIRECT, "8 times out of 501 recorded events",
            DOM, "15 times out of 213 recorded events"));

    private static final String THROWN_ERRORS = "Thrown Errors";
    private static final String ERRORS_PER_MINUTE = "an average of 130 errors per minute";
    private static final String INVERTED_PARALLELISM_RULE = "Inverted Parallelism";

    @TempDir
    static Path work;

    @Nested
    @DisplayName("What each run is told")
    class Findings {

        @ParameterizedTest
        @EnumSource(PersonsRecording.class)
        @DisplayName("every rule answers, and the warnings are the run's own")
        void everyRuleAnswersAndTheWarningsAreTheRunsOwn(PersonsRecording recording) {
            List<AutoAnalysisResult> results = AutoAnalysisDataProvider.generate(List.of(plain(recording)));

            assertEquals(RuleRegistry.getRules().size(), results.size());
            assertEquals(new TreeSet<>(WARNINGS.get(recording)), rules(results, Severity.WARNING));
            assertTrue(rules(results, Severity.INFO).containsAll(STABLE_INFO.get(recording)),
                    () -> rules(results, Severity.INFO).toString());
        }

        @ParameterizedTest
        @EnumSource(PersonsRecording.class)
        @DisplayName("the warnings state the figures the run holds")
        void theWarningsStateTheRunsFigures(PersonsRecording recording) {
            Map<String, AutoAnalysisResult> byRule = byRule(AutoAnalysisDataProvider.generate(List.of(plain(recording))));

            assertTrue(byRule.get(THROWN_ERRORS).summary().contains(ERRORS_PER_MINUTE), byRule.get(THROWN_ERRORS)::summary);
            assertTrue(byRule.get(INVERTED_PARALLELISM_RULE).summary().contains(INVERTED_PARALLELISM.get(recording)),
                    byRule.get(INVERTED_PARALLELISM_RULE)::summary);
        }
    }

    /** The rules see one recording, however it is handed to them. */
    @Nested
    @DisplayName("One run, however it is handed over")
    class HoweverHandedOver {

        @Test
        @DisplayName("the archive is judged as the file it compresses")
        void theArchiveIsJudgedAsThePlainFile() {
            assertEquals(stable(AutoAnalysisDataProvider.generate(List.of(plain(DIRECT)))),
                    stable(AutoAnalysisDataProvider.generate(List.of(archive(DIRECT)))));
        }

        /** The rules take every file at once and build one collection, so a run in pieces is the run. */
        @Test
        @DisplayName("its chunks as separate files are judged as the file")
        void theChunksAsFilesAreJudgedAsTheFile() {
            List<Path> pieces = JfrParser.disassemble(plain(DOM), work.resolve("pieces-" + DOM));

            assertEquals(stable(AutoAnalysisDataProvider.generate(List.of(plain(DOM)))),
                    stable(AutoAnalysisDataProvider.generate(pieces)));
        }
    }

    /** What may change between two evaluations of one file in one JVM, and what may not. */
    @Nested
    @DisplayName("Evaluated again")
    class EvaluatedAgain {

        @ParameterizedTest
        @EnumSource(PersonsRecording.class)
        @DisplayName("only the rules that read the recording's settings can change")
        void onlyTheSettingsDependentRulesCanChange(PersonsRecording recording) {
            Map<String, AutoAnalysisResult> first = byRule(AutoAnalysisDataProvider.generate(List.of(plain(recording))));
            Map<String, AutoAnalysisResult> again = byRule(AutoAnalysisDataProvider.generate(List.of(plain(recording))));

            Set<String> changed = first.keySet().stream()
                    .filter(rule -> !first.get(rule).equals(again.get(rule)))
                    .collect(Collectors.toCollection(TreeSet::new));
            assertTrue(SETTINGS_DEPENDENT.containsAll(changed), () -> "Changed between evaluations: " + changed);
        }
    }

    /** Every finding but the settings-dependent ones, by rule. */
    private static Map<String, AutoAnalysisResult> stable(List<AutoAnalysisResult> results) {
        Map<String, AutoAnalysisResult> stable = new TreeMap<>(byRule(results));
        stable.keySet().removeAll(SETTINGS_DEPENDENT);
        return stable;
    }

    private static Map<String, AutoAnalysisResult> byRule(List<AutoAnalysisResult> results) {
        Map<String, AutoAnalysisResult> byRule = new TreeMap<>();
        results.forEach(result -> byRule.put(result.rule(), result));
        return byRule;
    }

    private static Set<String> rules(List<AutoAnalysisResult> results, Severity severity) {
        Predicate<AutoAnalysisResult> atSeverity = result -> result.severity() == severity;
        return results.stream().filter(atSeverity).map(AutoAnalysisResult::rule)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Path archive(PersonsRecording recording) {
        Path archive = work.resolve(recording.fileName());
        if (Files.notExists(archive)) {
            try {
                Files.write(archive, recording.bytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return archive;
    }

    private static Path plain(PersonsRecording recording) {
        Path plain = work.resolve(recording + ".jfr");
        if (Files.notExists(plain)) {
            Lz4Compressor.decompress(archive(recording), plain);
        }
        return plain;
    }
}
