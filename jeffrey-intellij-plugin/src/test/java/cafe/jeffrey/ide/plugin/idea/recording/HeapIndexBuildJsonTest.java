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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The pipeline's progress, read down to the one line the panel draws. Thirteen stages come back;
 * what matters is which one is running, of how many, for how long, and whether it stopped.
 */
public class HeapIndexBuildJsonTest {

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
        HeapIndexBuild build = MicroscopeJson.parseIndexBuild(RUNNING);

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
        HeapIndexBuild build = MicroscopeJson.parseIndexBuild(
                "{\"state\":\"running\",\"stages\":[{\"id\":\"load\",\"status\":\"pending\"},"
                        + "{\"id\":\"parse\",\"status\":\"pending\"}]}");

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
        HeapIndexBuild build = MicroscopeJson.parseIndexBuild(WITH_SKIPPED_STAGE);

        assertEquals(3, build.stageNumber());
        assertEquals(4, build.stageCount());
        assertEquals("Dominator tree", build.stageTitle());
        assertEquals(10_000L, build.elapsedMs());
        assertEquals(2 / 4.0, build.fraction(), 1e-9);
    }

    /** With nothing in progress, the stage after the last finished one is the one to name — and a skipped stage is one. */
    @Test
    public void namesTheStageAfterASkippedOneWhenNothingIsRunning() {
        HeapIndexBuild build = MicroscopeJson.parseIndexBuild(SKIPPED_AND_NOTHING_RUNNING);

        assertEquals(3, build.stageNumber());
        assertEquals("Dominator tree", build.stageTitle());
        assertEquals(5_000L, build.elapsedMs());
    }

    /** Time spent on a skipped stage is still time the developer waited, failure or not. */
    @Test
    public void addsASkippedStagesDurationToTheElapsedTime() {
        HeapIndexBuild build = MicroscopeJson.parseIndexBuild(SKIPPED_THEN_FAILED);

        assertTrue(build.failed());
        assertEquals(3, build.stageNumber());
        assertEquals(7_000L, build.elapsedMs());
    }

    @Test
    public void carriesMicroscopesOwnWordsOnAFailure() {
        HeapIndexBuild build = MicroscopeJson.parseIndexBuild(
                "{\"state\":\"failed\",\"errorCode\":\"OOM\",\"errorMessage\":\"Java heap space\","
                        + "\"stages\":[{\"id\":\"load\",\"status\":\"completed\",\"durationMs\":4000},"
                        + "{\"id\":\"parse\",\"status\":\"failed\",\"durationMs\":9000}]}");

        assertTrue(build.failed());
        assertEquals(2, build.stageNumber());
        assertEquals("Java heap space", build.failureMessage());
    }

    @Test
    public void fallsBackToTheErrorCodeThenToAPlainSentence() {
        assertEquals("OOM", MicroscopeJson.parseIndexBuild(
                "{\"state\":\"failed\",\"errorCode\":\"OOM\",\"stages\":[]}").failureMessage());
        assertEquals("The index build did not finish.", MicroscopeJson.parseIndexBuild(
                "{\"state\":\"failed\",\"stages\":[]}").failureMessage());
    }

    /** Idle and completed are both "nothing to watch": the panel asks for the profile again. */
    @Test
    public void idleAndCompletedAreNoBuildAtAll() {
        assertNull(MicroscopeJson.parseIndexBuild("{\"state\":\"idle\",\"stages\":[]}"));
        assertNull(MicroscopeJson.parseIndexBuild("{\"state\":\"completed\",\"stages\":[]}"));
    }

    @Test
    public void showsAnUnknownStageIdAsItselfRatherThanHidingIt() {
        HeapIndexBuild build = new HeapIndexBuild(HeapIndexBuild.Phase.RUNNING, 1, 1, "new-stage", 0L, null);
        assertEquals("new-stage", build.stageTitle());
    }
}
