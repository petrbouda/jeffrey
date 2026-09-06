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

package cafe.jeffrey.ide.plugin.idea.agent;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Which agent the split button runs, and what happens to the ones it does not.
 *
 * <p>The choice used to be invisible: with a button each, whichever agent was installed got pressed
 * and order never mattered. A split button makes order decide something, so the rule that decides it
 * is worth pinning — otherwise an unrelated edit to {@code AgentCli.ALL} silently changes what a
 * developer's button does.
 */
public class AgentRowTest {

    private static final AgentCli CLAUDE = new AgentCli("Claude", "claude");
    private static final AgentCli CODEX = new AgentCli("Codex", "codex");
    private static final AgentCli GEMINI = new AgentCli("Gemini", "gemini");

    @Test
    public void thePreferredAgentWinsWhenItIsInstalled() {
        AgentRow row = row("gemini", entry(CLAUDE, true), entry(CODEX, false), entry(GEMINI, true));

        assertEquals(GEMINI, row.primary());
        assertTrue(row.hasInstalled());
    }

    /** An agent that was uninstalled since must not leave the button pointing at nothing. */
    @Test
    public void anUninstalledPreferenceFallsBackToTheFirstInstalled() {
        AgentRow row = row("gemini", entry(CLAUDE, true), entry(GEMINI, false));

        assertEquals(CLAUDE, row.primary());
    }

    @Test
    public void withNoPreferenceTheFirstInstalledLeads() {
        AgentRow row = row(null, entry(CODEX, false), entry(CLAUDE, true), entry(GEMINI, true));

        assertEquals(CLAUDE, row.primary());
    }

    @Test
    public void withNothingInstalledThereIsNoPrimary() {
        AgentRow row = row("claude", entry(CLAUDE, false), entry(CODEX, false));

        assertNull(row.primary());
        assertFalse(row.hasInstalled());
    }

    /** The menu opens with the button's own action at the top, so the two agree. */
    @Test
    public void thePrimaryLeadsTheReadyList() {
        AgentRow row = row("gemini", entry(CLAUDE, true), entry(CODEX, false), entry(GEMINI, true));

        assertEquals(List.of(GEMINI, CLAUDE), row.ready());
    }

    @Test
    public void missingHoldsEverySupportedAgentThatIsNotInstalled() {
        AgentRow row = row(null, entry(CLAUDE, true), entry(CODEX, false), entry(GEMINI, false));

        assertEquals(List.of(CODEX, GEMINI), row.missing());
    }

    @Test
    public void anAgentIsFoundByTheExecutableAClickCarriedBack() {
        AgentRow row = row(null, entry(CLAUDE, true), entry(CODEX, false));

        assertEquals(CODEX, row.byExecutable("codex"));
        assertNull(row.byExecutable("nothing-like-this"));
    }

    /** Two letters because the first collides: Claude and Codex both start with a C. */
    @Test
    public void marksDistinguishAgentsSharingAFirstLetter()  {
        assertEquals("Cl", CLAUDE.mark());
        assertEquals("Co", CODEX.mark());
        assertEquals("Ge", GEMINI.mark());
    }

    @Test
    public void everyShippedAgentHasATwoLetterMark() {
        for (AgentCli agent : AgentCli.ALL) {
            assertEquals(agent.displayName(), 2, agent.mark().length());
        }
    }

    private static AgentRow.Entry entry(AgentCli agent, boolean installed) {
        return new AgentRow.Entry(agent, installed);
    }

    /**
     * Builds a row without touching {@code PATH}. {@link AgentRow#resolve} asks the filesystem, which
     * would make every assertion here depend on what the machine running the build has installed.
     */
    private static AgentRow row(String preferred, AgentRow.Entry... entries) {
        List<AgentRow.Entry> list = List.of(entries);
        AgentCli primary = list.stream()
                .filter(e -> e.installed() && e.agent().executable().equals(preferred))
                .map(AgentRow.Entry::agent)
                .findFirst()
                .orElseGet(() -> list.stream()
                        .filter(AgentRow.Entry::installed)
                        .map(AgentRow.Entry::agent)
                        .findFirst()
                        .orElse(null));
        return new AgentRow(list, primary);
    }
}
