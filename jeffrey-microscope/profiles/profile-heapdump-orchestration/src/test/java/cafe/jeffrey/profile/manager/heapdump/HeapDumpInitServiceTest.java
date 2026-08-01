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
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.profile.common.pipeline.StageProgress;
import cafe.jeffrey.profile.common.pipeline.StageStatus;
import cafe.jeffrey.profile.common.pipeline.SubPhaseTiming;
import cafe.jeffrey.profile.heapdump.model.IndexBuildProgressListener;
import cafe.jeffrey.profile.heapdump.model.InitializeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeapDumpInitServiceTest {

    private static final String PROFILE_ID = "profile-1";

    @Mock
    HeapDumpManager manager;

    @Test
    void reportsLiveElapsedForInProgressStageAndClearsItOnCompletion() throws InterruptedException {
        MutableClock clock = new MutableClock(Instant.ofEpochMilli(10_000L));
        HeapDumpInitService service = new HeapDumpInitService(clock);

        CountDownLatch stageEntered = new CountDownLatch(1);
        CountDownLatch releaseStage = new CountDownLatch(1);
        // Block inside the index build before any sub-phase fires: the first
        // index-group stage (load) is in-progress and holds the live timer.
        when(manager.initialize(eq(null), any())).thenAnswer(invocation -> {
            stageEntered.countDown();
            releaseStage.await();
            return null;
        });

        assertTrue(service.start(PROFILE_ID, manager, null));
        assertTrue(stageEntered.await(5, TimeUnit.SECONDS), "index build must start");

        clock.advance(Duration.ofSeconds(7));

        StageProgress loadStage =
                stageById(service.progress(PROFILE_ID), HeapDumpStages.LOAD);
        assertEquals(StageStatus.IN_PROGRESS, loadStage.status());
        assertNotNull(loadStage.elapsedMs(), "the active stage must report live elapsed time");
        assertTrue(loadStage.elapsedMs() >= 7_000L,
                "elapsed must reflect the advanced clock, was: " + loadStage.elapsedMs());

        releaseStage.countDown();
        await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(
                PipelineState.COMPLETED, service.progress(PROFILE_ID).state()));

        StageProgress completedStage =
                stageById(service.progress(PROFILE_ID), HeapDumpStages.LOAD);
        assertEquals(StageStatus.COMPLETED, completedStage.status());
        assertNull(completedStage.elapsedMs(), "terminal stages carry durationMs, not live elapsed");
    }

    @Test
    void advancesLoadParseIndexStagesFromSubPhaseEvents() {
        HeapDumpInitService service = new HeapDumpInitService(
                Clock.fixed(Instant.ofEpochMilli(0L), ZoneOffset.UTC));

        // Sub-phases in the real HprofIndex emit (execution) order — monotonic
        // load → parse → index once bucketed by stage.
        List<SubPhaseTiming> subPhases = List.of(
                new SubPhaseTiming("walk_top_level", 100L, null),       // load
                new SubPhaseTiming("write_stack_traces", 20L, null),    // load
                new SubPhaseTiming("drop_indexes", 10L, null),          // load
                new SubPhaseTiming("walk_class_dumps", 200L, null),     // parse
                new SubPhaseTiming("walk_pass_b", 300L, null),          // parse
                new SubPhaseTiming("apply_shallow_correction", 40L, null), // parse
                new SubPhaseTiming("write_string_content", 60L, null),  // parse
                new SubPhaseTiming("write_metadata", 5L, null),         // index
                new SubPhaseTiming("create_indexes", 400L, null),       // index
                new SubPhaseTiming("checkpoint", 15L, null));           // index
        when(manager.initialize(eq(null), any())).thenAnswer(invocation -> {
            IndexBuildProgressListener listener = invocation.getArgument(1);
            for (SubPhaseTiming timing : subPhases) {
                listener.onSubPhaseStarted(timing.name());
                listener.onSubPhase(timing);
            }
            return new InitializeResult(null, subPhases);
        });

        assertTrue(service.start(PROFILE_ID, manager, null));
        await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(
                PipelineState.COMPLETED, service.progress(PROFILE_ID).state()));

        PipelineProgress progress = service.progress(PROFILE_ID);
        StageProgress load = stageById(progress, HeapDumpStages.LOAD);
        StageProgress parse = stageById(progress, HeapDumpStages.PARSE);
        StageProgress index = stageById(progress, HeapDumpStages.INDEX);

        // load = walk_top_level + write_stack_traces + drop_indexes.
        assertEquals(StageStatus.COMPLETED, load.status());
        assertEquals(130L, load.durationMs().longValue());
        assertEquals(3, load.subPhases().size());

        // parse = walk_class_dumps + walk_pass_b + apply_shallow_correction + write_string_content.
        assertEquals(StageStatus.COMPLETED, parse.status());
        assertEquals(600L, parse.durationMs().longValue());
        assertEquals(4, parse.subPhases().size());

        // index = write_metadata + create_indexes + checkpoint.
        assertEquals(StageStatus.COMPLETED, index.status());
        assertEquals(420L, index.durationMs().longValue());
        assertEquals(3, index.subPhases().size());
    }

    @Test
    void completesLoadTheMomentTheFirstParsePhaseStarts() throws InterruptedException {
        HeapDumpInitService service = new HeapDumpInitService(
                Clock.fixed(Instant.ofEpochMilli(0L), ZoneOffset.UTC));

        CountDownLatch parsePhaseStarted = new CountDownLatch(1);
        CountDownLatch releaseParsePhase = new CountDownLatch(1);
        AtomicReference<PipelineProgress> snapshotAtParseStart = new AtomicReference<>();

        when(manager.initialize(eq(null), any())).thenAnswer(invocation -> {
            IndexBuildProgressListener listener = invocation.getArgument(1);
            // Load-group phases run and complete.
            listener.onSubPhaseStarted("walk_top_level");
            listener.onSubPhase(new SubPhaseTiming("walk_top_level", 100L, null));
            listener.onSubPhaseStarted("drop_indexes");
            listener.onSubPhase(new SubPhaseTiming("drop_indexes", 10L, null));
            // The first parse phase STARTS. Load must already be completed here —
            // before this phase does any work — instead of lagging behind it.
            listener.onSubPhaseStarted("walk_class_dumps");
            snapshotAtParseStart.set(service.progress(PROFILE_ID));
            parsePhaseStarted.countDown();
            releaseParsePhase.await();
            listener.onSubPhase(new SubPhaseTiming("walk_class_dumps", 200L, null));
            return new InitializeResult(null, List.of());
        });

        assertTrue(service.start(PROFILE_ID, manager, null));
        assertTrue(parsePhaseStarted.await(5, TimeUnit.SECONDS), "parse phase must start");

        PipelineProgress atStart = snapshotAtParseStart.get();
        assertEquals(StageStatus.COMPLETED,
                stageById(atStart, HeapDumpStages.LOAD).status(),
                "load must be completed as soon as the first parse phase starts");
        assertEquals(StageStatus.IN_PROGRESS,
                stageById(atStart, HeapDumpStages.PARSE).status(),
                "parse must be active while its phase runs, not queued behind a completed load");

        releaseParsePhase.countDown();
        await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(
                PipelineState.COMPLETED, service.progress(PROFILE_ID).state()));
    }

    private static StageProgress stageById(PipelineProgress progress, String stageId) {
        return progress.stages().stream()
                .filter(stage -> stage.id().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage not found: " + stageId));
    }

    /** Manually advanced clock so live-elapsed assertions stay deterministic. */
    private static final class MutableClock extends Clock {

        private volatile Instant instant;

        private MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
