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

import java.util.List;

/**
 * The agents the panel offers, split by whether they are actually on {@code PATH}.
 *
 * <p>The row is a split button: one click runs {@link #primary()}, and the rest live behind its
 * chevron. That shape is what keeps the action row a fixed width as {@link AgentCli#ALL} grows —
 * three agents and eight cost the same space, where a button each does not.
 *
 * <p>An agent that is not installed stays in the menu rather than disappearing, under a heading that
 * says why it cannot be pressed. Jeffrey supporting Codex is a fact about Jeffrey, and a developer
 * should not have to read {@code AgentCli.ALL} to discover it.
 */
public record AgentRow(List<Entry> entries, AgentCli primary) {

    /** @param installed whether {@code PATH} holds the executable right now */
    public record Entry(AgentCli agent, boolean installed) {
    }

    public AgentRow {
        entries = List.copyOf(entries);
    }

    /**
     * Resolves every known agent, and picks the one the split button runs.
     *
     * <p>The preferred agent wins when it is installed, so the button keeps doing what it did last
     * time; otherwise the first installed one does. Without that, which agent a developer with two
     * installed gets would be decided by the order {@link AgentCli#ALL} happens to declare — invisible
     * from the button, and changed by an unrelated edit to that list.
     *
     * @param preferredExecutable the executable last launched, or null when nothing has been
     * @return a row whose {@link #primary()} is null when nothing at all is installed
     */
    public static AgentRow resolve(List<AgentCli> agents, String preferredExecutable) {
        List<Entry> entries = agents.stream()
                .map(agent -> new Entry(agent, agent.isInstalled()))
                .toList();

        AgentCli preferred = entries.stream()
                .filter(entry -> entry.installed() && entry.agent().executable().equals(preferredExecutable))
                .map(Entry::agent)
                .findFirst()
                .orElseGet(() -> entries.stream()
                        .filter(Entry::installed)
                        .map(Entry::agent)
                        .findFirst()
                        .orElse(null));

        return new AgentRow(entries, preferred);
    }

    /** Whether anything at all can be launched. With none installed the split button is disabled. */
    public boolean hasInstalled() {
        return primary != null;
    }

    /** Installed agents, primary first, so the menu opens with the button's own action at the top. */
    public List<AgentCli> ready() {
        return entries.stream()
                .filter(Entry::installed)
                .map(Entry::agent)
                .sorted((left, right) -> Boolean.compare(right.equals(primary), left.equals(primary)))
                .toList();
    }

    /** Agents Jeffrey supports that this machine does not have. */
    public List<AgentCli> missing() {
        return entries.stream()
                .filter(entry -> !entry.installed())
                .map(Entry::agent)
                .toList();
    }

    /** Looks an agent up by the executable name a click carried back from the page. */
    public AgentCli byExecutable(String executable) {
        return entries.stream()
                .map(Entry::agent)
                .filter(agent -> agent.executable().equals(executable))
                .findFirst()
                .orElse(null);
    }
}
