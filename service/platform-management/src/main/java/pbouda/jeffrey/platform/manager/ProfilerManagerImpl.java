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

import pbouda.jeffrey.common.model.ProfilerInfo;
import pbouda.jeffrey.provider.api.repository.ProfilerRepository;

import java.util.List;
import java.util.Optional;

public class ProfilerManagerImpl implements ProfilerManager {

    private final ProfilerRepository profilerRepository;

    public ProfilerManagerImpl(ProfilerRepository profilerRepository) {
        this.profilerRepository = profilerRepository;
    }

    @Override
    public void upsertSettings(ProfilerInfo profiler) {
        profilerRepository.upsertSettings(profiler);
    }

    @Override
    public Optional<ProfilerInfo> findSettings(String workspaceId, String projectId) {
        return profilerRepository.findSettings(workspaceId, projectId);
    }

    @Override
    public List<ProfilerInfo> findAllSettings() {
        return profilerRepository.findAllSettings();
    }

    @Override
    public void deleteSettings(String workspaceId, String projectId) {
        profilerRepository.deleteSettings(workspaceId, projectId);
    }
}
