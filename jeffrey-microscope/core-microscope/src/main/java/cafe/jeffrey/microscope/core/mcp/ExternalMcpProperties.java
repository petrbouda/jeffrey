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

package cafe.jeffrey.microscope.core.mcp;

import java.util.Set;

/**
 * Static configuration of the external MCP server at {@code /api/internal/mcp}.
 * <p>
 * Read once from {@code jeffrey.microscope.mcp.*} at wiring time rather than from the live settings:
 * the server is on by default, and whether an installation exposes it is a deployment decision made
 * alongside the bind address and the reverse proxy, not a switch a reader flips from the Settings page.
 * Turning either flag off is therefore an application property and takes a restart.
 * <p>
 * {@code ingestEnabled} is separate from {@code enabled} because the two answer different questions.
 * Reading is what the server is for; ingesting is the one thing it does that changes Jeffrey's state,
 * and it does so by opening a path on the machine Jeffrey runs on. An installation that wants the
 * original read-only posture back — a shared Jeffrey, say — turns ingestion off and keeps the rest.
 *
 * @param enabled       whether the endpoint answers at all; while off it responds {@code 404}
 * @param ingestEnabled whether the {@code recordings_} family is advertised, which is what lets a
 *                      client import a local recording file and build a profile from it. While off the
 *                      family is not advertised at all rather than advertised and refusing
 * @param hubsEnabled   whether the {@code hubs_} family is advertised, which lets a client list and
 *                      pull recordings from a connected Jeffrey Hub. Separate from
 *                      {@code ingestEnabled} because it is a larger permission — ingestion reads a
 *                      file on this machine, while this reaches out to remote infrastructure and can
 *                      move gigabytes off it — so an installation can allow the first and refuse the
 *                      second. It cannot be the more permissive of the two: everything it produces is
 *                      analysed by the {@code recordings_} family, so it is advertised only when
 *                      ingestion is on as well
 * @param computeEnabled whether the tools that build something before answering are advertised —
 *                      the heap-dump index, the dominator tree, a cached heap report, the auto-analysis
 *                      rule set. They read no differently from their neighbours, but each can occupy a
 *                      core for minutes and hold a large heap while it works, so an installation that
 *                      shares a machine can withhold them and leave the reading tools alone
 * @param families      the tool families to advertise, empty meaning all of them. A client that pays
 *                      for every schema on every turn can be given only the families it uses
 * @param token         a bearer token the endpoint requires, empty meaning none. Jeffrey has no
 *                      authentication anywhere else, so this is opt-in rather than a default that would
 *                      imply the rest of the API is protected too
 */
public record ExternalMcpProperties(
        boolean enabled,
        boolean ingestEnabled,
        boolean hubsEnabled,
        boolean computeEnabled,
        Set<String> families,
        String token) {

    public ExternalMcpProperties {
        families = families == null ? Set.of() : Set.copyOf(families);
        token = token == null ? "" : token.trim();
    }

    /**
     * Whether the {@code hubs_} family should be advertised. A hub download lands in the same store
     * ingestion governs and its next step is {@code recordings_analyzeRecording}, so advertising it
     * without ingestion would put a family in the model's context whose own descriptions point at a
     * tool that is not there.
     */
    public boolean hubsAdvertised() {
        return hubsEnabled && ingestEnabled;
    }

    /**
     * Whether a family is served at all.
     * <p>
     * An empty list means every family, which is what almost every installation wants. It is there for
     * the client that pays for the whole tool list on every turn — Codex loads every schema each time —
     * and for the reader who only ever asks one kind of question.
     */
    public boolean advertises(String family) {
        return families.isEmpty() || families.contains(family);
    }

    /**
     * Whether the endpoint requires a bearer token.
     */
    public boolean tokenRequired() {
        return !token.isEmpty();
    }
}
