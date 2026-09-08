/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
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
 * that carries the figures share an id and merge. The {@code nextTool} is the Jeffrey tool that
 * renders that topic's figures, so a fired rule routes the reader to the numbers rather than to the
 * rule's own suggestion.
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
     * JMC's {@code JfrRuleTopics} to the tool that carries the figures behind a rule in that topic.
     * A topic missing here yields a finding with no {@code nextTool}; the reader still has the
     * category and the summary.
     */
    private static final Map<String, String> NEXT_TOOL_BY_TOPIC = Map.ofEntries(
            entry("garbage_collection", "jvm_gc"),
            entry("gc_summary", "jvm_gc"),
            entry("gc_configuration", "jvm_gcDetail page=configuration"),
            entry("heap", "jvm_gc"),
            entry("tlab", "memory_allocations"),
            entry("memoryleak", "memory_leakCandidates"),
            entry("exceptions", "jvm_exceptions"),
            entry("classloading", "jvm_classLoading"),
            entry("code_cache", "jvm_jit"),
            entry("compilations", "jvm_jit"),
            entry("lock_instances", "blocking_monitors"),
            entry("biased_locking", "blocking_overview"),
            entry("threads", "jvm_threads"),
            entry("thread_dumps", "jvm_threadDumps"),
            entry("vm_operations", "jvm_safepoints"),
            entry("method_profiling", "flamegraph_export eventType=jdk.ExecutionSample"),
            entry("file_io", "io_overview kind=FILE"),
            entry("socket_io", "io_overview kind=SOCKET"),
            entry("jvm_information", "jvm_configuration"),
            entry("environment_variables", "jvm_configuration"),
            entry("system_properties", "jvm_configuration"),
            entry("agent_information", "jvm_configuration"),
            entry("system_information", "jvm_system"),
            entry("processes", "jvm_system"),
            entry("native_library", "jvm_nativeMemory"),
            entry("java_application", "jvm_threads"),
            entry("recording", "jfr_listEventTypes"),
            entry("constant_pools", "jfr_listEventTypes"));

    private AutoAnalysisFindings() {
    }

    /**
     * Every rule that reached a verdict, ordered by severity.
     */
    public static List<McpFinding> findings(List<AutoAnalysisResult> results) {
        return McpFindings.merge(results.stream()
                .map(AutoAnalysisFindings::finding)
                .flatMap(Optional::stream)
                .toList());
    }

    /**
     * Only the rules that flagged something — what a summary leads with. A pass is still a finding,
     * but not one to spend the first five lines of an orientation on.
     */
    public static List<McpFinding> flagged(List<AutoAnalysisResult> results) {
        return findings(results).stream()
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

    static Optional<McpFinding> finding(AutoAnalysisResult result) {
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
                .nextTool(NEXT_TOOL_BY_TOPIC.get(category))
                .build());
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
