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
 */
public record ExternalMcpProperties(boolean enabled, boolean ingestEnabled) {
}
