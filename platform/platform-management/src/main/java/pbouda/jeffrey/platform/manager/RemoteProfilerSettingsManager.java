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

package pbouda.jeffrey.platform.manager;

import pbouda.jeffrey.platform.manager.workspace.remote.RemoteProfilerClient;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;

/**
 * Remote implementation of ProfilerSettingsManager that calls the remote Jeffrey.
 * Used for REMOTE workspaces.
 */
public class RemoteProfilerSettingsManager implements ProfilerSettingsManager {

    private final RemoteProfilerClient profilerClient;
    private final WorkspaceInfo workspaceInfo;
    private final String projectId;

    public RemoteProfilerSettingsManager(
            RemoteProfilerClient profilerClient,
            WorkspaceInfo workspaceInfo,
            String projectId) {

        this.profilerClient = profilerClient;
        this.workspaceInfo = workspaceInfo;
        this.projectId = projectId;
    }

    @Override
    public EffectiveProfilerSettings fetchEffectiveSettings() {
        return profilerClient.fetchProfilerSettings(workspaceInfo.originId(), projectId);
    }

    @Override
    public void upsertSettings(String agentSettings) {
        profilerClient.upsertProfilerSettings(workspaceInfo.originId(), projectId, agentSettings);
    }

    @Override
    public void deleteSettings() {
        profilerClient.deleteProfilerSettings(workspaceInfo.originId(), projectId);
    }
}
