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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.PipelineRun;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRequest;
import cafe.jeffrey.profile.common.pipeline.SubPhaseTiming;
import cafe.jeffrey.profile.heapdump.model.IndexBuildProgressListener;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side heap-dump initialization pipeline: one POST starts the whole run in the background (index
 * build, dominator tree and every cached analysis), and the frontend polls {@link #progress(String)} for
 * live per-stage statuses instead of orchestrating eleven sequential requests itself.
 *
 * <p>The run bookkeeping — the registry, the per-stage timing, the live timer — is
 * {@link PipelineRunRegistry}, shared with the Advisor. What stays here is the only part that is
 * genuinely about heap dumps: which analyses run, in what order, and how the atomic index build is
 * surfaced as three stages.</p>
 *
 * <p>Runs are unbounded and never evicted, both deliberately: the work is local CPU and IO, so a
 * ceiling would only serialise a user against their own profiles, and the settings page shows the last
 * run as "last initialization" — a TTL would make that quietly disappear.</p>
 */
public final class HeapDumpInitService {

    /**
     * Sub-phase names (from {@code HprofIndex.build}) mapped onto the three index-group stages. The
     * buckets are chosen so the sub-phases arrive in monotonic {@code load → parse → index} order at
     * build time (the build's phase sequence is fixed for correctness and cannot be reordered):
     * {@code load} is the initial structural read plus its immediate DB prep (write stack traces, drop
     * indexes); {@code parse} decodes the object graph; every remaining sub-phase (write_metadata,
     * create_indexes, checkpoint) finalizes the index and falls through to {@code index}. Keep these in
     * sync with the sub-phase names emitted by {@code HprofIndex}.
     */
    private static final Set<String> LOAD_SUB_PHASES = Set.of(
            "walk_top_level", "write_stack_traces", "drop_indexes");

    private static final Set<String> PARSE_SUB_PHASES = Set.of(
            "walk_class_dumps", "walk_pass_b", "apply_shallow_correction", "write_string_content");

    private static final int STRING_ANALYSIS_TOP_N = 100;

    private static final int BIGGEST_OBJECTS_TOP_N = 20;

    private static final int BIGGEST_COLLECTIONS_TOP_N = 50;

    private static final int DUPLICATE_DATA_TOP_N = 50;

    private final PipelineRunRegistry<String> registry;

    public HeapDumpInitService(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.registry = new PipelineRunRegistry<>(
                HeapDumpStages.DEFINITION, PipelineRunOptions.unbounded(), clock);
    }

    /**
     * Starts a background init run for the profile. Returns {@code false} when a run is already in
     * progress (the caller should just poll), {@code true} when a new run was started.
     */
    public boolean start(String profileId, HeapDumpManager manager, Boolean compressedOopsOverride) {
        return registry.start(new PipelineRunRequest<>(
                profileId,
                "",
                run -> runPipeline(run, manager, compressedOopsOverride),
                manager::storeInitPipelineResult));
    }

    /** Live progress of the current (or last finished) run; idle when none exists. */
    public PipelineProgress progress(String profileId) {
        return registry.progress(profileId);
    }

    private static void runPipeline(
            PipelineRun run, HeapDumpManager manager, Boolean compressedOopsOverride) {

        runIndexGroup(run, manager, compressedOopsOverride);
        run.runStage(HeapDumpStages.STRINGS, () -> manager.runStringAnalysis(STRING_ANALYSIS_TOP_N));
        run.runStage(HeapDumpStages.DOMINATOR, manager::runComputeDominator);
        run.runStage(HeapDumpStages.THREADS, manager::runThreadAnalysis);
        run.runStage(HeapDumpStages.BIGGEST, () -> manager.runBiggestObjects(BIGGEST_OBJECTS_TOP_N));
        run.runStage(HeapDumpStages.COLLECTIONS, manager::runCollectionAnalysis);
        run.runStage(HeapDumpStages.LEAKS, manager::runLeakSuspects);
        run.runStage(HeapDumpStages.CLASSLOADERS, manager::runClassLoaderAnalysis);
        run.runStage(HeapDumpStages.BIGGEST_COLLECTIONS,
                () -> manager.runBiggestCollections(BIGGEST_COLLECTIONS_TOP_N));
        run.runStage(HeapDumpStages.CONSUMERS, manager::runConsumerReport);
        run.runStage(HeapDumpStages.DUPLICATES, () -> manager.runDuplicateData(DUPLICATE_DATA_TOP_N));
    }

    /**
     * Runs the atomic index build once, advancing the three {@link HeapDumpStages#INDEX_GROUP} stages
     * (load → parse → index) in real time as the build's sub-phases complete. The build reports each
     * sub-phase via {@link IndexBuildProgressListener}; the driver maps it to a stage and, when a later
     * stage is reached, completes the earlier ones — so the stages light up one at a time with real
     * per-stage durations.
     */
    private static void runIndexGroup(
            PipelineRun run, HeapDumpManager manager, Boolean compressedOopsOverride) {

        IndexGroupProgress progress = new IndexGroupProgress(run);
        try {
            manager.initialize(compressedOopsOverride, progress);
        } catch (RuntimeException e) {
            run.failStages(HeapDumpStages.INDEX_GROUP);
            throw e;
        }
        progress.finish();
    }

    private static String stageForSubPhase(String subPhaseName) {
        if (LOAD_SUB_PHASES.contains(subPhaseName)) {
            return HeapDumpStages.LOAD;
        }
        if (PARSE_SUB_PHASES.contains(subPhaseName)) {
            return HeapDumpStages.PARSE;
        }
        return HeapDumpStages.INDEX;
    }

    /**
     * Drives the load → parse → index stages from index-build sub-phase events. Transitions happen when
     * a sub-phase <em>starts</em> (via {@link #onSubPhaseStarted}), so a stage completes exactly at its
     * real phase boundary rather than lagging a phase behind — e.g. "load" finishes the moment the first
     * "parse" phase begins, not after it completes. Durations are accumulated from the completion events
     * ({@link #onSubPhase}). Sub-phases arrive in monotonic stage order (see {@link #LOAD_SUB_PHASES}).
     * Not thread-safe — the build fires the listener single-threaded.
     *
     * <p>This is why {@link PipelineRun} exposes {@code beginStage} and {@code completeStage} rather
     * than only {@code runStage}: three stages here are one call underneath, and only the build knows
     * where their boundaries fall.</p>
     */
    private static final class IndexGroupProgress implements IndexBuildProgressListener {

        private final PipelineRun run;
        private final Map<String, List<SubPhaseTiming>> subPhasesByStage = new LinkedHashMap<>();
        private int activeIndex;

        private IndexGroupProgress(PipelineRun run) {
            this.run = run;
            for (String id : HeapDumpStages.INDEX_GROUP) {
                subPhasesByStage.put(id, new ArrayList<>());
            }
            run.beginStage(HeapDumpStages.INDEX_GROUP.getFirst());
        }

        @Override
        public void onSubPhaseStarted(String subPhaseName) {
            int stageIndex = HeapDumpStages.INDEX_GROUP.indexOf(stageForSubPhase(subPhaseName));
            while (activeIndex < stageIndex) {
                completeStage(HeapDumpStages.INDEX_GROUP.get(activeIndex));
                activeIndex++;
                run.beginStage(HeapDumpStages.INDEX_GROUP.get(activeIndex));
            }
        }

        @Override
        public void onSubPhase(SubPhaseTiming timing) {
            subPhasesByStage.get(stageForSubPhase(timing.name())).add(timing);
        }

        void finish() {
            for (int i = activeIndex; i < HeapDumpStages.INDEX_GROUP.size(); i++) {
                completeStage(HeapDumpStages.INDEX_GROUP.get(i));
            }
            run.clearActiveStage();
        }

        private void completeStage(String stageId) {
            List<SubPhaseTiming> subs = subPhasesByStage.get(stageId);
            long durationMs = subs.stream().mapToLong(SubPhaseTiming::durationMs).sum();
            run.completeStage(stageId, durationMs, subs);
        }
    }
}
