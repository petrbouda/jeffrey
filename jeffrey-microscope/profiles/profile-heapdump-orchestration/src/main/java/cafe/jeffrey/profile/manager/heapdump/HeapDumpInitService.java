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

import cafe.jeffrey.profile.heapdump.model.HeapDumpInitProgress;
import cafe.jeffrey.profile.heapdump.model.HeapDumpInitStageProgress;
import cafe.jeffrey.profile.heapdump.model.InitPipelineResult;
import cafe.jeffrey.profile.heapdump.model.InitStageResult;
import cafe.jeffrey.profile.heapdump.model.InitializeResult;
import cafe.jeffrey.profile.heapdump.model.SubPhaseTiming;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Server-side heap-dump initialization pipeline: one POST starts the whole
 * run in the background (index build, dominator tree and every cached
 * analysis), and the frontend polls {@link #progress(String)} for live
 * per-stage statuses instead of orchestrating eleven sequential requests
 * itself.
 *
 * <p>Process-wide (managers are recreated per request), keyed by profile id.
 * At most one run per profile is active at a time; the terminal state stays
 * queryable until the next run replaces it. The final snapshot is persisted
 * through {@code HeapDumpManager.storeInitPipelineResult}, so the "last run"
 * summary the overview page shows is now backend-owned.
 */
public final class HeapDumpInitService {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpInitService.class);

    /** Stage ids shared with the frontend's timeline definition, in run order. */
    public static final String STAGE_INDEX = "index";
    public static final String STAGE_STRINGS = "strings";
    public static final String STAGE_DOMINATOR = "dominator";
    public static final String STAGE_THREADS = "threads";
    public static final String STAGE_BIGGEST = "biggest";
    public static final String STAGE_COLLECTIONS = "collections";
    public static final String STAGE_LEAKS = "leaks";
    public static final String STAGE_CLASSLOADERS = "classloaders";
    public static final String STAGE_BIGGEST_COLLECTIONS = "biggest-collections";
    public static final String STAGE_CONSUMERS = "consumers";
    public static final String STAGE_DUPLICATES = "duplicates";

    private static final List<String> STAGE_ORDER = List.of(
            STAGE_INDEX, STAGE_STRINGS, STAGE_DOMINATOR, STAGE_THREADS, STAGE_BIGGEST,
            STAGE_COLLECTIONS, STAGE_LEAKS, STAGE_CLASSLOADERS, STAGE_BIGGEST_COLLECTIONS,
            STAGE_CONSUMERS, STAGE_DUPLICATES);

    private static final int STRING_ANALYSIS_TOP_N = 100;

    private static final int BIGGEST_OBJECTS_TOP_N = 20;

    private static final int BIGGEST_COLLECTIONS_TOP_N = 50;

    private static final int DUPLICATE_DATA_TOP_N = 50;

    private final Clock clock;

    private final ConcurrentMap<String, RunState> runsByProfileId = new ConcurrentHashMap<>();

    public HeapDumpInitService(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
    }

    /**
     * Starts a background init run for the profile. Returns {@code false} when
     * a run is already in progress (the caller should just poll), {@code true}
     * when a new run was started.
     */
    public boolean start(String profileId, HeapDumpManager manager, Boolean compressedOopsOverride) {
        RunState fresh = new RunState();
        RunState existing = runsByProfileId.compute(profileId, (id, current) -> {
            if (current != null && current.isRunning()) {
                return current;
            }
            return fresh;
        });
        if (existing != fresh) {
            LOG.debug("Heap dump init already running: profileId={}", profileId);
            return false;
        }
        CompletableFuture
                .runAsync(() -> runPipeline(profileId, manager, compressedOopsOverride, fresh),
                        Schedulers.sharedVirtual())
                .exceptionally(ex -> {
                    LOG.error("Heap dump init pipeline crashed: profileId={}", profileId, ex);
                    return null;
                });
        return true;
    }

    /** Live progress of the current (or last finished) run; idle when none exists. */
    public HeapDumpInitProgress progress(String profileId) {
        RunState run = runsByProfileId.get(profileId);
        if (run == null) {
            return HeapDumpInitProgress.idle();
        }
        return run.snapshot();
    }

    private void runPipeline(
            String profileId, HeapDumpManager manager, Boolean compressedOopsOverride, RunState run) {
        Instant startedAt = clock.instant();
        try {
            run.runStage(STAGE_INDEX, () -> {
                InitializeResult result = manager.initialize(compressedOopsOverride);
                return result == null ? null : result.subPhases();
            });
            run.runStage(STAGE_STRINGS, () -> {
                manager.runStringAnalysis(STRING_ANALYSIS_TOP_N);
                return null;
            });
            run.runStage(STAGE_DOMINATOR, manager::runComputeDominator);
            run.runStage(STAGE_THREADS, () -> {
                manager.runThreadAnalysis();
                return null;
            });
            run.runStage(STAGE_BIGGEST, () -> {
                manager.runBiggestObjects(BIGGEST_OBJECTS_TOP_N);
                return null;
            });
            run.runStage(STAGE_COLLECTIONS, () -> {
                manager.runCollectionAnalysis();
                return null;
            });
            run.runStage(STAGE_LEAKS, () -> {
                manager.runLeakSuspects();
                return null;
            });
            run.runStage(STAGE_CLASSLOADERS, () -> {
                manager.runClassLoaderAnalysis();
                return null;
            });
            run.runStage(STAGE_BIGGEST_COLLECTIONS, () -> {
                manager.runBiggestCollections(BIGGEST_COLLECTIONS_TOP_N);
                return null;
            });
            run.runStage(STAGE_CONSUMERS, () -> {
                manager.runConsumerReport();
                return null;
            });
            run.runStage(STAGE_DUPLICATES, () -> {
                manager.runDuplicateData(DUPLICATE_DATA_TOP_N);
                return null;
            });

            Instant completedAt = clock.instant();
            persistSnapshot(manager, run, startedAt, completedAt);
            run.complete();
            LOG.info("Heap dump init pipeline completed: profileId={} duration_ms={}",
                    profileId, Duration.between(startedAt, completedAt).toMillis());
        } catch (RuntimeException e) {
            String code = e instanceof JeffreyException jeffreyException
                    ? jeffreyException.getCode().name()
                    : null;
            run.fail(code, e.getMessage());
            LOG.warn("Heap dump init pipeline failed: profileId={} error_code={} error={}",
                    profileId, code, e.getMessage());
        }
    }

    private static void persistSnapshot(
            HeapDumpManager manager, RunState run, Instant startedAt, Instant completedAt) {
        List<InitStageResult> stages = new ArrayList<>();
        for (HeapDumpInitStageProgress stage : run.snapshot().stages()) {
            stages.add(new InitStageResult(
                    stage.id(), stage.status(), stage.durationMs(), stage.subPhases()));
        }
        InitPipelineResult snapshot = new InitPipelineResult(
                Duration.between(startedAt, completedAt).toMillis(),
                stages.size(),
                (int) stages.stream()
                        .filter(s -> HeapDumpInitStageProgress.STATUS_COMPLETED.equals(s.status()))
                        .count(),
                completedAt,
                stages);
        try {
            manager.storeInitPipelineResult(snapshot);
        } catch (RuntimeException e) {
            LOG.warn("Failed to persist init pipeline snapshot: error={}", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface StageWork {

        /** Runs the stage; may return sub-phase timings for the accordion, or {@code null}. */
        List<SubPhaseTiming> run();
    }

    /** Mutable per-run state; all stage mutations happen on the single pipeline thread. */
    private static final class RunState {

        private final Map<String, HeapDumpInitStageProgress> stages = new LinkedHashMap<>();
        private volatile String state = HeapDumpInitProgress.STATE_RUNNING;
        private volatile String errorCode;
        private volatile String errorMessage;

        private RunState() {
            synchronized (stages) {
                for (String id : STAGE_ORDER) {
                    stages.put(id, new HeapDumpInitStageProgress(
                            id, HeapDumpInitStageProgress.STATUS_PENDING, null, null));
                }
            }
        }

        boolean isRunning() {
            return HeapDumpInitProgress.STATE_RUNNING.equals(state);
        }

        void runStage(String id, StageWork work) {
            updateStage(id, HeapDumpInitStageProgress.STATUS_IN_PROGRESS, null, null);
            try {
                Elapsed<List<SubPhaseTiming>> elapsed = Measuring.s(work::run);
                updateStage(id, HeapDumpInitStageProgress.STATUS_COMPLETED,
                        elapsed.duration().toMillis(), elapsed.entity());
            } catch (RuntimeException e) {
                updateStage(id, HeapDumpInitStageProgress.STATUS_FAILED, null, null);
                throw e;
            }
        }

        void complete() {
            state = HeapDumpInitProgress.STATE_COMPLETED;
        }

        void fail(String code, String message) {
            errorCode = code;
            errorMessage = message;
            state = HeapDumpInitProgress.STATE_FAILED;
        }

        HeapDumpInitProgress snapshot() {
            List<HeapDumpInitStageProgress> ordered;
            synchronized (stages) {
                ordered = List.copyOf(stages.values());
            }
            return new HeapDumpInitProgress(state, errorCode, errorMessage, ordered);
        }

        private void updateStage(String id, String status, Long durationMs, List<SubPhaseTiming> subPhases) {
            synchronized (stages) {
                stages.put(id, new HeapDumpInitStageProgress(id, status, durationMs, subPhases));
            }
        }
    }
}
