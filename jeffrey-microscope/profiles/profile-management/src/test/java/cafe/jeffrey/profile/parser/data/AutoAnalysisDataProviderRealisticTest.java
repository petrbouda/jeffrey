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
import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.test.recording.TestappRecording;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static cafe.jeffrey.test.recording.TestappRecording.DIRECT;
import static cafe.jeffrey.test.recording.TestappRecording.DOM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JMC rules over realistic recordings: two runs of one service under load, which the rules
 * judge — errors thrown, the GC's parallelism and setup, the process it ran as, what is allocated and
 * by whom, what was sampled most.
 *
 * <p>Each file is a {@code jfrsync} recording: the JDK's chain and async-profiler's both define
 * {@code jdk.ActiveSetting} and {@code jdk.ActiveRecording}, with different fields. Loaded together,
 * the two definitions race and the rules that read the recording's settings judged the same file
 * differently from one evaluation to the next; loaded one chain at a time and merged, the JDK's
 * first, every rule answers alike every time. That is held here: the whole list of answers, again
 * and however the run is handed over.
 */
@DisplayName("Auto-analysis over realistic recordings")
class AutoAnalysisDataProviderRealisticTest {

    /** The rules that read the recording's settings: each once answered differently on each evaluation. */
    private static final Set<String> SETTINGS_READERS = Set.of(
            "Method Profiling", "Allocated Classes", "Threads Allocating", "TLAB Allocation Ratio",
            "GC Freed Ratio", "GC Pressure");

    /** What each run is warned about: the same service in the same container, so the same three. */
    private static final Map<TestappRecording, Set<String>> WARNINGS = new EnumMap<>(Map.of(
            DIRECT, Set.of("Thrown Errors", "Inverted Parallelism", "PID-1 Rule"),
            DOM, Set.of("Thrown Errors", "Inverted Parallelism", "PID-1 Rule")));

    /** What each run is told beside its warnings. */
    private static final Map<TestappRecording, Set<String>> NOTES = new EnumMap<>(Map.of(
            DIRECT, Set.of("Allocated Classes", "Threads Allocating", "GC Setup", "Metaspace Live Set Trend",
                    "Method Profiling", "Process Started"),
            DOM, Set.of("Allocated Classes", "Threads Allocating", "GC Setup", "Metaspace Live Set Trend",
                    "Method Profiling", "Process Started")));

    /** How often the GC's parallelism inverted, of how many events: the figure the warning reports. */
    private static final Map<TestappRecording, String> INVERTED_PARALLELISM = new EnumMap<>(Map.of(
            DIRECT, "6 times out of 22 recorded events",
            DOM, "5 times out of 26 recorded events"));

    /** How many errors a minute each run threw: the figure the warning reports. */
    private static final Map<TestappRecording, String> ERRORS_PER_MINUTE = new EnumMap<>(Map.of(
            DIRECT, "an average of 129 errors per minute",
            DOM, "an average of 131 errors per minute"));

    private static final String THROWN_ERRORS = "Thrown Errors";
    private static final String INVERTED_PARALLELISM_RULE = "Inverted Parallelism";
    private static final String ALLOCATED_CLASSES = "Allocated Classes";
    private static final String MOST_ALLOCATED = "The most allocated type is likely ''byte[]''";

    private static final Set<Severity> NO_ANSWER = Set.of(Severity.NA, Severity.IGNORE);

    @TempDir
    static Path work;

    /** Each run judged once from its plain file, for every test that only reads the judgement. */
    private static final Map<TestappRecording, List<AutoAnalysisResult>> JUDGED = new ConcurrentHashMap<>();

    @Nested
    @DisplayName("What each run is told")
    class Findings {

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        @DisplayName("every rule answers; the warnings and notes are the run's own")
        void theWarningsAndNotesAreTheRunsOwn(TestappRecording recording) {
            List<AutoAnalysisResult> results = judged(recording);

            assertEquals(RuleRegistry.getRules().size(), results.size());
            assertEquals(new TreeSet<>(WARNINGS.get(recording)), rules(results, Severity.WARNING));
            assertEquals(new TreeSet<>(NOTES.get(recording)), rules(results, Severity.INFO));
        }

        /** Each of them once answered "not enough samples" or "ignored" on one evaluation in two. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        @DisplayName("the rules that read the recording's settings all answer")
        void theRulesThatReadTheSettingsAllAnswer(TestappRecording recording) {
            Map<String, AutoAnalysisResult> byRule = byRule(judged(recording));

            for (String rule : SETTINGS_READERS) {
                assertTrue(!NO_ANSWER.contains(byRule.get(rule).severity()), () -> rule + ": " + byRule.get(rule));
            }
            assertTrue(byRule.get(ALLOCATED_CLASSES).summary().startsWith(MOST_ALLOCATED), byRule.get(ALLOCATED_CLASSES)::summary);
        }

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        @DisplayName("the warnings state the figures the run holds")
        void theWarningsStateTheRunsFigures(TestappRecording recording) {
            Map<String, AutoAnalysisResult> byRule = byRule(judged(recording));

            assertTrue(byRule.get(THROWN_ERRORS).summary().contains(ERRORS_PER_MINUTE.get(recording)),
                    byRule.get(THROWN_ERRORS)::summary);
            assertTrue(byRule.get(INVERTED_PARALLELISM_RULE).summary().contains(INVERTED_PARALLELISM.get(recording)),
                    byRule.get(INVERTED_PARALLELISM_RULE)::summary);
        }
    }

    /** Every answer, however often and however the run is handed to the rules. */
    @Nested
    @DisplayName("Alike every time")
    class AlikeEveryTime {

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        @DisplayName("judged again, every answer is the same")
        void judgedAgainEveryAnswerIsTheSame(TestappRecording recording) {
            assertEquals(judged(recording), provider().generate(List.of(plain(recording))));
        }

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        @DisplayName("the archive is judged as the file it compresses")
        void theArchiveIsJudgedAsThePlainFile(TestappRecording recording) {
            assertEquals(judged(recording), provider().generate(List.of(archive(recording))));
        }

        /** The rules take every file at once, so a run in pieces is the run. */
        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        @DisplayName("its chunks as separate files are judged as the file")
        void theChunksAsFilesAreJudgedAsTheFile(TestappRecording recording) {
            List<Path> pieces = JfrParser.disassemble(plain(recording), work.resolve("pieces-" + recording));

            assertEquals(judged(recording), provider().generate(pieces));
        }
    }

    private static List<AutoAnalysisResult> judged(TestappRecording recording) {
        return JUDGED.computeIfAbsent(recording, r -> provider().generate(List.of(plain(r))));
    }

    private static AutoAnalysisDataProvider provider() {
        return new AutoAnalysisDataProvider(TempDirFactory.of(work.resolve("scratch")));
    }

    private static Map<String, AutoAnalysisResult> byRule(List<AutoAnalysisResult> results) {
        Map<String, AutoAnalysisResult> byRule = new TreeMap<>();
        results.forEach(result -> byRule.put(result.rule(), result));
        return byRule;
    }

    private static Set<String> rules(List<AutoAnalysisResult> results, Severity severity) {
        return results.stream().filter(result -> result.severity() == severity).map(AutoAnalysisResult::rule)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Path archive(TestappRecording recording) {
        Path archive = work.resolve(recording + ".jfr.lz4");
        if (Files.notExists(archive)) {
            Lz4Compressor.compress(plain(recording), archive);
        }
        return archive;
    }

    private static Path plain(TestappRecording recording) {
        Path plain = work.resolve(recording + ".jfr");
        if (Files.notExists(plain)) {
            try {
                Files.write(plain, recording.bytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return plain;
    }
}
