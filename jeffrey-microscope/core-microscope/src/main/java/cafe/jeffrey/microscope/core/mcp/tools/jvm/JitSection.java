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

import cafe.jeffrey.profile.common.event.JITCompilationStats;
import cafe.jeffrey.profile.common.event.JITDeoptimizationMethodAggregate;
import cafe.jeffrey.profile.common.event.JITDeoptimizationReasonCount;
import cafe.jeffrey.profile.common.event.JITDeoptimizationStats;
import cafe.jeffrey.profile.common.event.JITLongCompilation;
import cafe.jeffrey.profile.manager.JITCompilationManager;
import cafe.jeffrey.profile.manager.JITDeoptimizationManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.jit.CodeCacheData;
import cafe.jeffrey.shared.common.model.Type;

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
public record JitSection(ProfileManager profileManager) implements JvmSection {

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
    public Object render() {
        JITCompilationManager compilationManager = profileManager.jitCompilationManager();
        JITDeoptimizationManager deoptimizationManager = profileManager.jitDeoptimizationManager();

        return new JitDashboard(
                compilationManager.statistics(),
                compilations(compilationManager.compilations(COMPILATIONS_LIMIT)),
                codeCache(compilationManager.codeCache()),
                deoptimization(deoptimizationManager));
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

        return new Deoptimization(stats, methods, reasons);
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
    private record JitDashboard(
            JITCompilationStats statistics,
            List<Compilation> compilations,
            CodeCache codeCache,
            Deoptimization deoptimization) {
    }

    private record Compilation(
            String method,
            String compiler,
            long compileLevel,
            double compileMillis,
            long codeSizeBytes,
            boolean onStackReplacement,
            boolean succeeded) {
    }

    /**
     * @param fullCount how many times a code heap ran full — anything above zero means compilation
     *                  stopped and the application kept running interpreted
     */
    private record CodeCache(long fullCount, List<CodeHeap> heaps) {
    }

    private record CodeHeap(
            String name,
            long reservedBytes,
            long usedBytes,
            long unallocatedBytes,
            long methodCount,
            long fullCount) {
    }

    private record Deoptimization(
            JITDeoptimizationStats statistics,
            List<DeoptimizedMethod> topMethods,
            List<DeoptimizationReason> reasons) {
    }

    private record DeoptimizedMethod(
            String method,
            long count,
            long distinctReasons,
            String dominantReason,
            long dominantReasonCount) {
    }

    private record DeoptimizationReason(String reason, long count) {
    }
}
