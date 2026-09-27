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

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.event.JITCompilationStats;
import cafe.jeffrey.profile.common.event.JITDeoptimizationMethodAggregate;
import cafe.jeffrey.profile.common.event.JITDeoptimizationReasonCount;
import cafe.jeffrey.profile.common.event.JITDeoptimizationStats;
import cafe.jeffrey.profile.common.event.JITLongCompilation;
import cafe.jeffrey.profile.manager.JITCompilationManager;
import cafe.jeffrey.profile.manager.JITDeoptimizationManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.jit.CodeCacheData;
import cafe.jeffrey.profile.mcp.McpFollowUp;

import java.util.List;
import java.util.Set;

/**
 * The JIT Compilation dashboard: what the compilers did, what they undid, and whether they ran out of
 * room to keep doing it.
 * <p>
 * Two things here are not visible in the events themselves. {@code jdk.Compilation} only fires above
 * the recording's threshold, so an empty compilation list means nothing compiled <em>slowly</em>
 * rather than that nothing compiled — {@link JITCompilationStats} carries the totals either way. And a
 * full code cache stops compilation altogether, leaving the application at interpreted speed for the
 * rest of its life with nothing in a CPU profile to say why.
 * <p>
 * Deoptimisation is where the JIT becomes a latency problem, so it is aggregated by method and by
 * reason rather than listed: one method deoptimised over and over ran interpreted for part of the
 * recording, and the reason is the pointer into the source.
 */
public record JitSection(ProfileManager profileManager) implements JvmSection<JitSection.JitDashboard> {

    public static final String ID = "jit";

    private static final String TITLE = "JIT Compilation";

    /** Slowest compilations carried back — the ones that delayed reaching peak speed. */
    private static final int COMPILATIONS_LIMIT = 15;

    /** Methods named in the deoptimisation aggregate, worst first. */
    private static final int DEOPT_METHODS_LIMIT = 15;

    private static final double NANOS_IN_MILLI = 1_000_000d;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.COMPILATION,
            Type.COMPILER_STATISTICS,
            Type.DEOPTIMIZATION,
            Type.CODE_CACHE_STATISTICS,
            Type.COMPILER_QUEUE_UTILIZATION);

    private static final String THRESHOLD_GUIDANCE =
            "An empty compilations list means nothing crossed the recording's threshold, not that nothing "
                    + "compiled; the statistics are there either way.";
    private static final String DEOPTIMISATION_GUIDANCE =
            "A deoptimisation reason and method point into source. Read the method rather than inferring "
                    + "what it does from its name.";
    private static final String COMPILER_THREADS_WHY =
            "shows the compiler threads' share of the CPU beside the application's; on a JDK 25 "
                    + "recording jdk.CPUTimeSample is the same question";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public Set<Type> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public MicroscopeView view() {
        return MicroscopeView.JIT_COMPILATION;
    }

    @Override
    public void followUp(NextSteps.Builder next, JitDashboard dashboard) {
        String profileId = profileManager.info().id();
        SectionCalls.onCpu(next, profileManager, eventType -> SectionCalls.on(SectionCalls.FLAMEGRAPH_EXPORT, profileId)
                .with(SectionCalls.EVENT_TYPE, eventType)
                .why(COMPILER_THREADS_WHY));
        next.guidance(THRESHOLD_GUIDANCE)
                .guidanceWhen(!dashboard.deoptimization().topMethods().isEmpty(), DEOPTIMISATION_GUIDANCE);
    }

    @Override
    public JitDashboard render() {
        JITCompilationManager compilationManager = profileManager.jitCompilationManager();
        JITDeoptimizationManager deoptimizationManager = profileManager.jitDeoptimizationManager();

        return new JitDashboard(
                statistics(compilationManager.statistics()),
                compilations(compilationManager.compilations(COMPILATIONS_LIMIT)),
                codeCache(compilationManager.codeCache()),
                deoptimization(deoptimizationManager));
    }

    private static Statistics statistics(JITCompilationStats stats) {
        if (stats == null) {
            return null;
        }
        return new Statistics(
                stats.compileCount(),
                stats.bailoutCount(),
                stats.invalidatedCount(),
                stats.osrCompileCount(),
                stats.standardCompileCount(),
                stats.nmethodsSize(),
                stats.nmethodCodeSize(),
                stats.peakTimeSpent(),
                stats.totalTimeSpent(),
                stats.compileMethodThreshold());
    }

    private static List<Compilation> compilations(List<JITLongCompilation> compilations) {
        return compilations.stream()
                .map(compilation -> new Compilation(
                        compilation.method(),
                        compilation.compiler() == null ? null : compilation.compiler().name(),
                        compilation.compileLevel(),
                        millis(compilation.duration()),
                        compilation.codeSize(),
                        compilation.isOsr(),
                        compilation.succeded()))
                .toList();
    }

    private static CodeCache codeCache(CodeCacheData data) {
        List<CodeHeap> heaps = data.segments().stream()
                .map(segment -> new CodeHeap(
                        segment.codeBlobType(),
                        segment.reservedBytes(),
                        segment.usedBytes(),
                        segment.unallocatedBytes(),
                        segment.methodCount(),
                        segment.fullCount()))
                .toList();

        return new CodeCache(data.codeCacheFullCount(), heaps);
    }

    private static Deoptimization deoptimization(JITDeoptimizationManager manager) {
        JITDeoptimizationStats stats = manager.statistics();

        List<DeoptimizedMethod> methods = manager.topMethods(DEOPT_METHODS_LIMIT).stream()
                .map(JitSection::deoptimizedMethod)
                .toList();

        List<DeoptimizationReason> reasons = manager.reasonDistribution().stream()
                .map(JitSection::deoptimizationReason)
                .toList();

        return new Deoptimization(deoptimizationStatistics(stats), methods, omittedMethods(stats, methods.size()), reasons);
    }

    /**
     * How many deoptimised methods the ranking left out. The count of distinct methods comes only from
     * the statistics event; without it a ranking at its cap cannot know how many more there were, so
     * the answer is null rather than a zero that reads as "none". A ranking short of its cap left
     * nothing out either way.
     */
    private static Long omittedMethods(JITDeoptimizationStats stats, int shown) {
        if (stats != null) {
            return Math.max(0, stats.distinctMethods() - shown);
        }
        return shown < DEOPT_METHODS_LIMIT ? 0L : null;
    }

    private static DeoptimizationStatistics deoptimizationStatistics(JITDeoptimizationStats stats) {
        if (stats == null) {
            return null;
        }
        return new DeoptimizationStatistics(
                stats.totalCount(),
                stats.distinctMethods(),
                stats.distinctReasons(),
                stats.topReason(),
                stats.topReasonCount(),
                stats.topMethod(),
                stats.topMethodCount(),
                stats.c1Count(),
                stats.c2Count());
    }

    private static DeoptimizedMethod deoptimizedMethod(JITDeoptimizationMethodAggregate aggregate) {
        return new DeoptimizedMethod(
                aggregate.method(),
                aggregate.count(),
                aggregate.distinctReasons(),
                aggregate.dominantReason(),
                aggregate.dominantReasonCount());
    }

    private static DeoptimizationReason deoptimizationReason(JITDeoptimizationReasonCount reason) {
        return new DeoptimizationReason(reason.reason(), reason.count());
    }

    private static double millis(long nanos) {
        return nanos / NANOS_IN_MILLI;
    }

    /**
     * @param statistics   the compiler's own totals, present even when no single compilation crossed
     *                     the recording's threshold; null when the recording carries no compiler
     *                     statistics at all
     * @param compilations the slowest individual compilations, which are the ones the threshold let
     *                     through
     */
    public record JitDashboard(
            @McpNullable
            Statistics statistics,
            @McpDescription("The " + COMPILATIONS_LIMIT + " slowest compilations the recording's threshold let through")
            List<Compilation> compilations,
            CodeCache codeCache,
            Deoptimization deoptimization) {
    }

    /**
     * The compiler's own totals, from {@code jdk.CompilerStatistics}.
     *
     * @param peakTimeSpentNanos  the longest single compilation
     * @param totalTimeSpentNanos every compilation together
     */
    public record Statistics(
            long compileCount,
            long bailoutCount,
            long invalidatedCount,
            long osrCompileCount,
            long standardCompileCount,
            long nmethodsSizeBytes,
            long nmethodCodeSizeBytes,
            long peakTimeSpentNanos,
            long totalTimeSpentNanos,
            long compileMethodThreshold) {
    }

    public record Compilation(
            @McpNullable
            String method,
            @McpNullable
            String compiler,
            long compileLevel,
            double compileMs,
            long codeSizeBytes,
            boolean onStackReplacement,
            boolean succeeded) {
    }

    /**
     * @param fullCount how many times a code heap ran full — anything above zero means compilation
     *                  stopped and the application kept running interpreted
     */
    public record CodeCache(long fullCount, List<CodeHeap> heaps) {
    }

    public record CodeHeap(
            String name,
            long reservedBytes,
            long usedBytes,
            long unallocatedBytes,
            long methodCount,
            long fullCount) {
    }

    public record Deoptimization(
            @McpNullable
            DeoptimizationStatistics statistics,
            @McpDescription("The " + DEOPT_METHODS_LIMIT + " methods deoptimised most often")
            List<DeoptimizedMethod> topMethods,
            @McpNullable
            @McpDescription("How many further deoptimised methods the list leaves out; null when the list is "
                    + "at its cap and the recording lacks the deoptimisation statistics that count them")
            Long omittedMethods,
            List<DeoptimizationReason> reasons) {
    }

    /**
     * @param topReason the commonest reason; null when nothing was deoptimised
     * @param topMethod the method deoptimised most often; null when nothing was
     */
    public record DeoptimizationStatistics(
            long totalCount,
            long distinctMethods,
            long distinctReasons,
            @McpNullable
            String topReason,
            long topReasonCount,
            @McpNullable
            String topMethod,
            long topMethodCount,
            long c1Count,
            long c2Count) {
    }

    public record DeoptimizedMethod(
            String method,
            long count,
            long distinctReasons,
            @McpNullable
            String dominantReason,
            long dominantReasonCount) {
    }

    public record DeoptimizationReason(
            @McpNullable
            String reason,
            long count) {
    }

    /**
     * What {@code jvm_jit} answers: the envelope every section shares, around this section's dashboard.
     */
    public record Answer(
            SectionStatus status,
            @McpNullable
            @McpDescription(SectionHeader.REASON)
            String reason,
            String profileId,
            @McpDescription(SectionHeader.SECTION)
            String section,
            String title,
            @McpNullable
            @McpDescription(SectionHeader.DASHBOARD)
            JitDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, JitDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
