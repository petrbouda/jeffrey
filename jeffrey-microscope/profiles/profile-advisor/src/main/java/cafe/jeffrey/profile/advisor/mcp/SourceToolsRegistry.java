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

package cafe.jeffrey.profile.advisor.mcp;

import cafe.jeffrey.profile.advisor.source.SourceAnalysisTools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps an advisor run to the {@link SourceAnalysisTools} bound to that run's source folder. The Claude
 * Code CLI reaches the tools out-of-process over the MCP endpoint, which resolves them here by
 * {@code runId}.
 *
 * <p>An entry lives only for the duration of a single run: the service registers the tools before
 * invoking the backend and unregisters them in a {@code finally} block. That window is what stops a CLI
 * from reading a user's source folder after the run that authorized it has ended.</p>
 */
public final class SourceToolsRegistry {

    private final Map<String, SourceAnalysisTools> toolsByRun = new ConcurrentHashMap<>();

    public void register(String runId, SourceAnalysisTools tools) {
        toolsByRun.put(runId, tools);
    }

    public void unregister(String runId) {
        toolsByRun.remove(runId);
    }

    /**
     * @return the tools registered for {@code runId}
     * @throws IllegalArgumentException if no run is registered (e.g. the CLI calls back after the run
     *                                  has finished and its tools were unregistered)
     */
    public SourceAnalysisTools resolve(String runId) {
        SourceAnalysisTools tools = toolsByRun.get(runId);
        if (tools == null) {
            throw new IllegalArgumentException("Unknown advisor run: " + runId);
        }
        return tools;
    }
}
