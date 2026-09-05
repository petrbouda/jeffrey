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
 */
public record ExternalMcpProperties(boolean enabled, boolean ingestEnabled, boolean hubsEnabled) {

    /**
     * Whether the {@code hubs_} family should be advertised. A hub download lands in the same store
     * ingestion governs and its next step is {@code recordings_analyzeRecording}, so advertising it
     * without ingestion would put a family in the model's context whose own descriptions point at a
     * tool that is not there.
     */
    public boolean hubsAdvertised() {
        return hubsEnabled && ingestEnabled;
    }
}
