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

package cafe.jeffrey.ide.plugin.idea.agent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the panel hands an agent.
 *
 * <p>The sentence is the whole contract between this plugin and a skill in another repository: each
 * one triggers a different skill, and there is no error anywhere if it stops matching — the agent
 * simply starts from nothing. So the phrases are pinned here.
 */
public class AgentTaskTest {

    private static final String PROFILE_ID = "01a0769f-a1bb-744f-962d-88b314030196";

    private static final String BASELINE_ID = "9f2c1b04-77aa-4e31-8d05-2b1c6f0e4432";

    /**
     * The phrase analyze-jfr's description fires on. Both agents get the same sentence — the skill
     * ships to Claude Code and to Codex from one source, and only the MCP tool prefix differs.
     */
    @Test
    public void sendsTheProfileIdAndTheSkillsTriggerPhrase() {
        assertEquals(
                "Analyse Jeffrey profile " + PROFILE_ID,
                new AgentTask.AnalyseRecording(PROFILE_ID).prompt());
    }

    /**
     * A heap dump goes to analyze-heap, which fires on "a heap dump". Sending one to the recording
     * skill would have the agent reach for flamegraph tools against a profile that has none.
     */
    @Test
    public void namesAHeapDumpSoTheHeapSkillTriggers() {
        String prompt = new AgentTask.AnalyseHeapDump(PROFILE_ID).prompt();

        assertTrue(prompt.contains("heap dump"));
        assertTrue(prompt.contains(PROFILE_ID));
        assertFalse(prompt.contains(".hprof"));
    }

    /** A pair goes to compare-jfr, which fires on comparing two profiles. */
    @Test
    public void namesBothProfilesSoTheCompareSkillTriggers() {
        assertEquals(
                "Compare Jeffrey profile " + PROFILE_ID + " against baseline " + BASELINE_ID,
                new AgentTask.Compare(PROFILE_ID, BASELINE_ID).prompt());
    }

    /**
     * The direction, which is the one thing a comparison's wording has to carry. Read the other way
     * round, the skill passes the two ids to compare_movements reversed and every regression in the
     * report comes back as an improvement.
     */
    @Test
    public void namesThePrimaryFirstAndTheBaselineAsTheBaseline() {
        String prompt = new AgentTask.Compare(PROFILE_ID, BASELINE_ID).prompt();

        assertTrue(prompt.indexOf(PROFILE_ID) < prompt.indexOf(BASELINE_ID));
        assertTrue(prompt.contains("against baseline " + BASELINE_ID));
    }

    /**
     * No baked-in question, in any of them. The panel does not know what the developer wants to know,
     * and a recording that lost a third of its samples is the case that proves it: an opener about
     * where the time goes would have the agent rank hot paths that are biased exactly where it
     * matters.
     */
    @Test
    public void asksNoQuestionOfItsOwn() {
        for (AgentTask task : everyTask()) {
            assertFalse(task.prompt(), task.prompt().contains("?"));
            assertFalse(task.prompt(), task.prompt().contains("—"));
        }
    }

    /** And never a file path: neither agent can parse a JFR or an hprof. */
    @Test
    public void neverMentionsTheRecordingFile() {
        for (AgentTask task : everyTask()) {
            assertFalse(task.prompt(), task.prompt().contains(".jfr"));
            assertFalse(task.prompt(), task.prompt().contains(".hprof"));
            assertFalse(task.prompt(), task.prompt().contains("/"));
        }
    }

    private static AgentTask[] everyTask() {
        return new AgentTask[]{
                new AgentTask.AnalyseRecording(PROFILE_ID),
                new AgentTask.AnalyseHeapDump(PROFILE_ID),
                new AgentTask.Compare(PROFILE_ID, BASELINE_ID)};
    }
}
