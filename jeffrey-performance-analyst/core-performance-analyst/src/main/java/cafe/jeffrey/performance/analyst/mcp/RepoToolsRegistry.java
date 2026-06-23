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

package cafe.jeffrey.performance.analyst.mcp;

import cafe.jeffrey.performance.analyst.recommendations.RepoAnalysisTools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a recommendation run to the {@link RepoAnalysisTools} bound to that run's ephemeral repository
 * clone. The Claude Code CLI reaches the tools out-of-process via {@link RepoMcpStreamableHttpController},
 * which resolves them here by {@code runId}.
 * <p>
 * An entry lives only for the duration of a single recommendation generation: the manager registers the
 * tools before invoking the backend and unregisters them in a {@code finally} block, so the clone (and
 * its registry entry) never outlive the call.
 */
public final class RepoToolsRegistry {

    private final Map<String, RepoAnalysisTools> toolsByRun = new ConcurrentHashMap<>();

    public void register(String runId, RepoAnalysisTools tools) {
        toolsByRun.put(runId, tools);
    }

    public void unregister(String runId) {
        toolsByRun.remove(runId);
    }

    /**
     * @return the tools registered for {@code runId}
     * @throws IllegalArgumentException if no run is registered (e.g. the CLI calls back after the run
     *                                  has finished and the clone was deleted)
     */
    public RepoAnalysisTools resolve(String runId) {
        RepoAnalysisTools tools = toolsByRun.get(runId);
        if (tools == null) {
            throw new IllegalArgumentException("Unknown recommendation run: " + runId);
        }
        return tools;
    }
}
