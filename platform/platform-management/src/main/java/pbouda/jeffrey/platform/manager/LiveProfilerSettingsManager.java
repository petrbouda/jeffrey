/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import pbouda.jeffrey.platform.EffectiveSettingsResolver;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings;
import pbouda.jeffrey.shared.common.model.ProfilerInfo;

import java.util.List;

/**
 * Live implementation of ProfilerSettingsManager that uses the ProfilerRepository.
 * Used for LIVE workspaces.
 */
public class LiveProfilerSettingsManager implements ProfilerSettingsManager {

    private final ProfilerRepository profilerRepository;
    private final String workspaceId;
    private final String projectId;

    public LiveProfilerSettingsManager(
            ProfilerRepository profilerRepository,
            String workspaceId,
            String projectId) {

        this.profilerRepository = profilerRepository;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
    }

    @Override
    public EffectiveProfilerSettings fetchEffectiveSettings() {
        List<ProfilerInfo> allSettings = profilerRepository.fetchProfilerSettings(workspaceId, projectId);
        return EffectiveSettingsResolver.resolve(allSettings);
    }

    @Override
    public void upsertSettings(String agentSettings) {
        ProfilerInfo profilerInfo = new ProfilerInfo(workspaceId, projectId, agentSettings);
        profilerRepository.upsertSettings(profilerInfo);
    }

    @Override
    public void deleteSettings() {
        profilerRepository.deleteSettings(workspaceId, projectId);
    }
}
