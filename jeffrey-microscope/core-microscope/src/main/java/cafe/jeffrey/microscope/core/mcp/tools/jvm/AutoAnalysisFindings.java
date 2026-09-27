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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.profile.mcp.finding.McpFindings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Map.entry;

/**
 * The JMC rule set's results in the shape every judging tool shares.
 * <p>
 * A rule result has three fates. One that ran and reached a verdict — {@code WARNING}, {@code INFO}
 * or {@code OK} — becomes a finding; the pass is kept, because "the rule checked and found nothing"
 * is evidence a reader wants, not a non-event. One that could not run ({@code NA}, or a rule the
 * toolkit ignored) is <em>not</em> a finding of any severity: it is a question the recording could
 * not answer, and it is reported as such under {@link #notEvaluated}, so that a reader never mistakes
 * "did not run" for "passed".
 * <p>
 * The category is the rule's JMC topic — {@code garbage_collection}, {@code exceptions},
 * {@code lock_instances} — which is what lets a finding from the rules and one from the dashboard
 * that carries the figures share an id and merge. The {@code nextTool} is the call, on the same
 * profile, that renders that topic's figures, so a fired rule routes the reader to the numbers rather
 * than to the rule's own suggestion.
 */
public final class AutoAnalysisFindings {

    public static final String SOURCE = "jvm_autoAnalysis";

    /** A result cached before the topic was recorded lands here rather than in no category at all. */
    static final String UNTOPICED_CATEGORY = "auto-analysis";

    private static final String EVIDENCE_RULE = "rule";
    private static final String EVIDENCE_SCORE = "score";

    private static final Set<AnalysisResult.Severity> NOT_EVALUATED =
            Set.of(AnalysisResult.Severity.NA, AnalysisResult.Severity.IGNORE);

    /**
     * JMC's {@code JfrRuleTopics} to the call that carries the figures behind a rule in that topic.
     * A topic missing here yields a finding with no {@code nextTool}; the reader still has the
     * category and the summary.
     */
    private static final Map<String, FigureCall> NEXT_TOOL_BY_TOPIC = Map.ofEntries(
            entry("garbage_collection", FigureCall.of("jvm_gc")),
            entry("gc_summary", FigureCall.of("jvm_gc")),
            entry("gc_configuration", FigureCall.of("jvm_gcDetail", "page", GcDetailPage.CONFIGURATION.name())),
            entry("heap", FigureCall.of("jvm_gc")),
            entry("tlab", FigureCall.of("memory_allocations")),
            entry("memoryleak", FigureCall.of("memory_leakCandidates")),
            entry("exceptions", FigureCall.of("jvm_exceptions")),
            entry("classloading", FigureCall.of("jvm_classLoading")),
            entry("code_cache", FigureCall.of("jvm_jit")),
            entry("compilations", FigureCall.of("jvm_jit")),
            entry("lock_instances", FigureCall.of("blocking_monitors")),
            entry("biased_locking", FigureCall.of("blocking_overview")),
            entry("threads", FigureCall.of("jvm_threads")),
            entry("thread_dumps", FigureCall.of("jvm_threadDumps")),
            entry("vm_operations", FigureCall.of("jvm_safepoints")),
            // No recorded-type gate: the method-profiling rule only emits a finding when jdk.ExecutionSample
            // was evaluated, so a finding in this topic means the profile recorded it.
            entry("method_profiling", FigureCall.of("flamegraph_export", "eventType", Type.EXECUTION_SAMPLE.code())),
            entry("file_io", FigureCall.of("io_overview", "kind", "FILE")),
            entry("socket_io", FigureCall.of("io_overview", "kind", "SOCKET")),
            entry("jvm_information", FigureCall.of("jvm_configuration")),
            entry("environment_variables", FigureCall.of("jvm_configuration")),
            entry("system_properties", FigureCall.of("jvm_configuration")),
            entry("agent_information", FigureCall.of("jvm_configuration")),
            entry("system_information", FigureCall.of("jvm_system")),
            entry("processes", FigureCall.of("jvm_system")),
            entry("native_library", FigureCall.of("jvm_nativeMemory")),
            entry("java_application", FigureCall.of("jvm_threads")),
            entry("recording", FigureCall.of("jfr_listEventTypes")),
            entry("constant_pools", FigureCall.of("jfr_listEventTypes")));

    private static final String PROFILE_ID = "profileId";
    private static final String FIGURES_WHY = "shows the figures this finding rests on";

    private AutoAnalysisFindings() {
    }

    /**
     * Every rule that reached a verdict, ordered by severity, each routed to the call on this profile
     * that carries its figures. The call is not gated here: the tool that hands the list out drops
     * the calls its installation does not serve, with {@link McpFindings#reachable}.
     */
    public static List<McpFinding> findings(String profileId, List<AutoAnalysisResult> results) {
        return McpFindings.merge(results.stream()
                .map(result -> finding(profileId, result))
                .flatMap(Optional::stream)
                .toList());
    }

    /**
     * Only the rules that flagged something — what a summary leads with. A pass is still a finding,
     * but not one to spend the first five lines of an orientation on.
     */
    public static List<McpFinding> flagged(String profileId, List<AutoAnalysisResult> results) {
        return findings(profileId, results).stream()
                .filter(finding -> finding.severity() != McpFinding.Severity.OK)
                .toList();
    }

    /**
     * The rules that could not run on this recording, by name. Not findings: gaps.
     */
    public static List<String> notEvaluated(List<AutoAnalysisResult> results) {
        return results.stream()
                .filter(result -> NOT_EVALUATED.contains(result.severity()))
                .map(AutoAnalysisResult::rule)
                .toList();
    }

    /** The topics a finding is routed from, for a test that follows every route. */
    static Set<String> routedTopics() {
        return NEXT_TOOL_BY_TOPIC.keySet();
    }

    static Optional<McpFinding> finding(String profileId, AutoAnalysisResult result) {
        if (result.severity() == null || NOT_EVALUATED.contains(result.severity())) {
            return Optional.empty();
        }
        String category = result.topic() == null || result.topic().isBlank()
                ? UNTOPICED_CATEGORY
                : result.topic();
        return Optional.of(McpFinding.of(category, result.rule())
                .severity(severity(result.severity()))
                .title(result.summary())
                .detail(result.explanation())
                .source(SOURCE)
                .evidence(EVIDENCE_RULE, result.rule())
                .evidence(EVIDENCE_SCORE, result.score())
                .action(result.solution())
                .nextTool(Optional.ofNullable(NEXT_TOOL_BY_TOPIC.get(category))
                        .map(call -> call.on(profileId))
                        .orElse(null))
                .build());
    }

    /**
     * The tool that carries a topic's figures, with the arguments beyond the profile that pick the
     * right page of it where the tool shows more than one.
     */
    private record FigureCall(String tool, Map<String, String> arguments) {

        static FigureCall of(String tool) {
            return new FigureCall(tool, Map.of());
        }

        static FigureCall of(String tool, String argument, String value) {
            return new FigureCall(tool, Map.of(argument, value));
        }

        McpNextTool on(String profileId) {
            McpNextTool.Call call = McpNextTool.call(tool).with(PROFILE_ID, profileId);
            arguments.forEach(call::with);
            return call.why(FIGURES_WHY);
        }
    }

    private static McpFinding.Severity severity(AnalysisResult.Severity severity) {
        return switch (severity) {
            case WARNING -> McpFinding.Severity.WARNING;
            case INFO -> McpFinding.Severity.INFO;
            case OK -> McpFinding.Severity.OK;
            // Guarded by the caller; listed so a new JMC severity fails to compile here rather than
            // being quietly filed as informational.
            case NA, IGNORE -> throw new IllegalArgumentException(
                    "A rule that was not evaluated is not a finding: severity=" + severity);
        };
    }
}
