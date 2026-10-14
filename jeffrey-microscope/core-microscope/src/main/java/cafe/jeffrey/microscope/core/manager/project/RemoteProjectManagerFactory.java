/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.manager.project;

import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.client.RemoteMappers;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.recordings.core.OriginContext;

/**
 * Turns a project the hub reported into its manager. Everything the manager needs comes with the
 * project itself — its workspace id included — so building one takes no further
 * call to the hub, whether the project came from a workspace listing or from a single lookup.
 */
public class RemoteProjectManagerFactory {

    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final RecordingsManager recordingsManager;

    public RemoteProjectManagerFactory(
            MicroscopeJeffreyDirs jeffreyDirs,
            ProfilesManager.Factory profilesManagerFactory,
            RecordingsManager recordingsManager) {

        this.jeffreyDirs = jeffreyDirs;
        this.profilesManagerFactory = profilesManagerFactory;
        this.recordingsManager = recordingsManager;
    }

    public ProjectManager create(HubInfo hubInfo, HubClients hubClients, RemoteProjectResponse project) {
        OriginContext originContext = new OriginContext(
                hubInfo.hubId(),
                hubInfo.name(),
                project.workspaceId(),
                project.id(),
                project.name());

        return new RemoteProjectManager(
                jeffreyDirs,
                RemoteMappers.toDetailedProjectInfo(project),
                new RepositoryStatistics(project.sizeBytes()),
                hubClients,
                profilesManagerFactory,
                recordingsManager,
                originContext);
    }
}
