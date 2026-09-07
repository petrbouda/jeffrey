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

package cafe.jeffrey.ide.plugin.idea.recording;

import org.junit.Test;

import static cafe.jeffrey.ide.plugin.idea.recording.PipelineBuild.Pipeline.HEAP_INDEX;
import static cafe.jeffrey.ide.plugin.idea.recording.PipelineBuild.Pipeline.PROFILE_INIT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The pipeline's progress, read down to the one line the panel draws. Thirteen stages come back;
 * what matters is which one is running, of how many, for how long, and whether it stopped.
 */
public class PipelineBuildJsonTest {

    private static final String RUNNING = "{\"pipelineId\":\"heap-init\",\"state\":\"running\",\"stages\":["
            + "{\"id\":\"load\",\"status\":\"completed\",\"durationMs\":4000},"
            + "{\"id\":\"parse\",\"status\":\"completed\",\"durationMs\":26000},"
            + "{\"id\":\"index\",\"status\":\"completed\",\"durationMs\":30000},"
            + "{\"id\":\"strings\",\"status\":\"completed\",\"durationMs\":5000},"
            + "{\"id\":\"dominator\",\"status\":\"in_progress\",\"elapsedMs\":15000},"
            + "{\"id\":\"threads\",\"status\":\"pending\"},"
            + "{\"id\":\"biggest\",\"status\":\"pending\"}]}";

    /** A run in which the string analysis found nothing to do and was skipped. */
    private static final String WITH_SKIPPED_STAGE = "{\"state\":\"running\",\"stages\":["
            + "{\"id\":\"load\",\"status\":\"completed\",\"durationMs\":4000},"
            + "{\"id\":\"strings\",\"status\":\"skipped\",\"durationMs\":1000},"
            + "{\"id\":\"dominator\",\"status\":\"in_progress\",\"elapsedMs\":5000},"
            + "{\"id\":\"threads\",\"status\":\"pending\"}]}";

    /** The same run in the moment between two stages: one finished, one skipped, none started yet. */
    private static final String SKIPPED_AND_NOTHING_RUNNING = "{\"state\":\"running\",\"stages\":["
            + "{\"id\":\"load\",\"status\":\"completed\",\"durationMs\":4000},"
            + "{\"id\":\"strings\",\"status\":\"skipped\",\"durationMs\":1000},"
            + "{\"id\":\"dominator\",\"status\":\"pending\"},"
            + "{\"id\":\"threads\",\"status\":\"pending\"}]}";

    private static final String SKIPPED_THEN_FAILED = "{\"state\":\"failed\",\"errorMessage\":\"Java heap space\","
            + "\"stages\":[{\"id\":\"load\",\"status\":\"completed\",\"durationMs\":4000},"
            + "{\"id\":\"strings\",\"status\":\"skipped\",\"durationMs\":1000},"
            + "{\"id\":\"dominator\",\"status\":\"failed\",\"durationMs\":2000}]}";

    @Test
    public void namesTheRunningStageAndAddsUpTheTimeSoFar() {
        PipelineBuild build = MicroscopeJson.parseBuild(RUNNING, HEAP_INDEX);

        assertFalse(build.failed());
        assertEquals(5, build.stageNumber());
        assertEquals(7, build.stageCount());
        assertEquals("Dominator tree", build.stageTitle());
        assertEquals(80_000L, build.elapsedMs());
        assertEquals(4 / 7.0, build.fraction(), 1e-9);
    }

    /** Right after the request nothing is in progress yet; the first stage is the one to name. */
    @Test
    public void aRunThatHasNotStartedAStageNamesTheFirst() {
        PipelineBuild build = MicroscopeJson.parseBuild(
                "{\"state\":\"running\",\"stages\":[{\"id\":\"load\",\"status\":\"pending\"},"
                        + "{\"id\":\"parse\",\"status\":\"pending\"}]}", HEAP_INDEX);

        assertEquals(1, build.stageNumber());
        assertEquals("Loading the dump", build.stageTitle());
        assertEquals(0.0, build.fraction(), 1e-9);
    }

    /**
     * A skipped stage is finished. The backend's {@code StageStatus} treats it as terminal, and
     * counting it as neither running nor done left the line naming a stage that had already gone by
     * and under-reporting the time behind it for the rest of the run.
     */
    @Test
    public void countsASkippedStageAsFinished() {
        PipelineBuild build = MicroscopeJson.parseBuild(WITH_SKIPPED_STAGE, HEAP_INDEX);

        assertEquals(3, build.stageNumber());
        assertEquals(4, build.stageCount());
        assertEquals("Dominator tree", build.stageTitle());
        assertEquals(10_000L, build.elapsedMs());
        assertEquals(2 / 4.0, build.fraction(), 1e-9);
    }

    /** With nothing in progress, the stage after the last finished one is the one to name — and a skipped stage is one. */
    @Test
    public void namesTheStageAfterASkippedOneWhenNothingIsRunning() {
        PipelineBuild build = MicroscopeJson.parseBuild(SKIPPED_AND_NOTHING_RUNNING, HEAP_INDEX);

        assertEquals(3, build.stageNumber());
        assertEquals("Dominator tree", build.stageTitle());
        assertEquals(5_000L, build.elapsedMs());
    }

    /** Time spent on a skipped stage is still time the developer waited, failure or not. */
    @Test
    public void addsASkippedStagesDurationToTheElapsedTime() {
        PipelineBuild build = MicroscopeJson.parseBuild(SKIPPED_THEN_FAILED, HEAP_INDEX);

        assertTrue(build.failed());
        assertEquals(3, build.stageNumber());
        assertEquals(7_000L, build.elapsedMs());
    }

    @Test
    public void carriesMicroscopesOwnWordsOnAFailure() {
        PipelineBuild build = MicroscopeJson.parseBuild(
                "{\"state\":\"failed\",\"errorCode\":\"OOM\",\"errorMessage\":\"Java heap space\","
                        + "\"stages\":[{\"id\":\"load\",\"status\":\"completed\",\"durationMs\":4000},"
                        + "{\"id\":\"parse\",\"status\":\"failed\",\"durationMs\":9000}]}", HEAP_INDEX);

        assertTrue(build.failed());
        assertEquals(2, build.stageNumber());
        assertEquals("Java heap space", build.failureMessage());
    }

    @Test
    public void fallsBackToTheErrorCodeThenToAPlainSentence() {
        assertEquals("OOM", MicroscopeJson.parseBuild(
                "{\"state\":\"failed\",\"errorCode\":\"OOM\",\"stages\":[]}", HEAP_INDEX).failureMessage());
        assertEquals("The index build did not finish.", MicroscopeJson.parseBuild(
                "{\"state\":\"failed\",\"stages\":[]}", HEAP_INDEX).failureMessage());
    }

    /** Idle and completed are both "nothing to watch": the panel asks for the profile again. */
    @Test
    public void idleAndCompletedAreNoBuildAtAll() {
        assertNull(MicroscopeJson.parseBuild("{\"state\":\"idle\",\"stages\":[]}", HEAP_INDEX));
        assertNull(MicroscopeJson.parseBuild("{\"state\":\"completed\",\"stages\":[]}", HEAP_INDEX));
    }

    @Test
    public void showsAnUnknownStageIdAsItselfRatherThanHidingIt() {
        PipelineBuild build = new PipelineBuild(HEAP_INDEX, PipelineBuild.Phase.RUNNING, 1, 1, "new-stage", 0L, null);
        assertEquals("new-stage", build.stageTitle());
    }

    /**
     * The other pipeline, through the same parser. Both endpoints answer the same
     * {@code PipelineProgress}, so what differs is only which words the stage ids go by — and they
     * have to differ: {@code parse} exists in both, and a shared title map would tell a reader that
     * their recording was "Parsing objects", which is what a heap dump does.
     */
    @Test
    public void readsTheProfilePipelineWithItsOwnWords() {
        PipelineBuild build = MicroscopeJson.parseBuild(
                "{\"state\":\"running\",\"stages\":["
                        + "{\"id\":\"profile-info\",\"status\":\"completed\",\"durationMs\":40},"
                        + "{\"id\":\"parse\",\"status\":\"in_progress\",\"elapsedMs\":12000}]}",
                PROFILE_INIT);

        assertEquals(2, build.stageNumber());
        assertEquals("Reading the recording", build.stageTitle());
        assertEquals(12_040L, build.elapsedMs());
    }

    /**
     * The same id, the other pipeline. Pinned as a pair because the collision is the whole reason the
     * build carries its pipeline rather than looking the title up in one shared map.
     */
    @Test
    public void tellsTheTwoParseStagesApart() {
        String parsing = "{\"state\":\"running\",\"stages\":[{\"id\":\"parse\",\"status\":\"in_progress\"}]}";

        assertEquals("Parsing objects", MicroscopeJson.parseBuild(parsing, HEAP_INDEX).stageTitle());
        assertEquals("Reading the recording", MicroscopeJson.parseBuild(parsing, PROFILE_INIT).stageTitle());
    }

    /**
     * Two of the profile pipeline's nine stages are routinely skipped — method trace weights unless
     * the recording traced methods, traces unless it carries spans — so counting a skipped stage as
     * finished matters more here than for a dump, where skipping is the exception.
     */
    @Test
    public void countsTheProfilePipelinesRoutinelySkippedStagesAsFinished() {
        PipelineBuild build = MicroscopeJson.parseBuild(
                "{\"state\":\"running\",\"stages\":["
                        + "{\"id\":\"profile-info\",\"status\":\"completed\",\"durationMs\":40},"
                        + "{\"id\":\"parse\",\"status\":\"completed\",\"durationMs\":9000},"
                        + "{\"id\":\"method-trace-weights\",\"status\":\"skipped\"},"
                        + "{\"id\":\"traces\",\"status\":\"skipped\"},"
                        + "{\"id\":\"checkpoint\",\"status\":\"in_progress\",\"elapsedMs\":500}]}",
                PROFILE_INIT);

        assertEquals(5, build.stageNumber());
        assertEquals("Writing the database", build.stageTitle());
    }

    @Test
    public void namesTheProfilePipelineWhenAnAnalysisFailsWithoutSaying() {
        PipelineBuild build =
                MicroscopeJson.parseBuild("{\"state\":\"failed\",\"stages\":[]}", PROFILE_INIT);

        assertEquals("The analysis did not finish.", build.failureMessage());
    }

    /**
     * A real answer, taken off a running Microscope while it analysed one of the repository's own
     * recordings. Two things in it are worth pinning: the very first stage came back
     * {@code skipped} — a recording imported by path has no workspace details to read — and the
     * pipeline reports nine stages, so the numbering a reader sees has to count that skip as behind
     * them rather than ahead.
     */
    @Test
    public void readsARunItActuallyObserved() {
        PipelineBuild build = MicroscopeJson.parseBuild(
                "{\"pipelineId\":\"profile-init\",\"scopeId\":\"01a07c36\",\"state\":\"running\","
                        + "\"errorCode\":null,\"errorMessage\":null,\"stages\":["
                        + "{\"id\":\"profile-info\",\"status\":\"skipped\"},"
                        + "{\"id\":\"parse\",\"status\":\"completed\",\"durationMs\":240},"
                        + "{\"id\":\"flush\",\"status\":\"in_progress\",\"elapsedMs\":27},"
                        + "{\"id\":\"method-trace-weights\",\"status\":\"pending\"},"
                        + "{\"id\":\"recluster\",\"status\":\"pending\"},"
                        + "{\"id\":\"traces\",\"status\":\"pending\"},"
                        + "{\"id\":\"additional-files\",\"status\":\"pending\"},"
                        + "{\"id\":\"checkpoint\",\"status\":\"pending\"},"
                        + "{\"id\":\"warmup\",\"status\":\"pending\"}],\"running\":true}",
                PROFILE_INIT);

        assertFalse(build.failed());
        assertEquals(3, build.stageNumber());
        assertEquals(9, build.stageCount());
        assertEquals("Collecting event types and threads", build.stageTitle());
        assertEquals(267L, build.elapsedMs());
    }

    /** Idle is what the endpoint answers for a profile nothing is building — including one that never existed. */
    @Test
    public void readsTheIdleAnswerTheEndpointActuallyGives() {
        assertNull(MicroscopeJson.parseBuild(
                "{\"pipelineId\":\"profile-init\",\"scopeId\":\"\",\"state\":\"idle\","
                        + "\"errorCode\":null,\"errorMessage\":null,\"stages\":[],\"running\":false}",
                PROFILE_INIT));
    }
}
