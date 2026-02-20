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

package pbouda.jeffrey.platform.manager.workspace.remote;

import org.springframework.http.ResponseEntity;
import pbouda.jeffrey.platform.resources.pub.PublicApiPaths;
import pbouda.jeffrey.platform.resources.request.ProfilerSettingsRequest;
import pbouda.jeffrey.platform.resources.response.ProfilerSettingsResponse;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings;

public class RemoteProfilerClientImpl implements RemoteProfilerClient {

    private final RemoteHttpInvoker invoker;

    public RemoteProfilerClientImpl(RemoteHttpInvoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public EffectiveProfilerSettings fetchProfilerSettings(String workspaceId, String projectId) {
        ResponseEntity<ProfilerSettingsResponse> settings = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(PublicApiPaths.PROFILER_SETTINGS, workspaceId, projectId)
                    .retrieve()
                    .toEntity(ProfilerSettingsResponse.class);
        });

        ProfilerSettingsResponse body = settings.getBody();
        return body != null ? body.toModel() : EffectiveProfilerSettings.none();
    }

    @Override
    public void upsertProfilerSettings(String workspaceId, String projectId, String agentSettings) {
        invoker.post(() -> {
            return invoker.restClient().post()
                    .uri(PublicApiPaths.PROFILER_SETTINGS, workspaceId, projectId)
                    .body(new ProfilerSettingsRequest(workspaceId, projectId, agentSettings))
                    .retrieve()
                    .toBodilessEntity();
        });
    }

    @Override
    public void deleteProfilerSettings(String workspaceId, String projectId) {
        invoker.delete(() -> {
            return invoker.restClient().delete()
                    .uri(PublicApiPaths.PROFILER_SETTINGS, workspaceId, projectId)
                    .retrieve()
                    .toBodilessEntity();
        });
    }
}
