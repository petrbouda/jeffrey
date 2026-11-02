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

import cafe.jeffrey.microscope.connector.api.manager.ConnectorInstancesManager;
import cafe.jeffrey.microscope.connector.api.manager.ConnectorRepositoryManager;
import cafe.jeffrey.microscope.connector.api.manager.RepositoryManager;

import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.connector.api.ProjectScope;
import cafe.jeffrey.microscope.core.manager.*;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.recordings.core.OriginContext;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.recordings.core.ConnectorRecordingsDownloadManager;
import cafe.jeffrey.microscope.model.ProjectInfo;

public class ConnectorProjectManager implements ProjectManager {

    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final ProjectInfo projectInfo;
    private final RepositoryStatistics repositoryStatistics;
    private final ConnectorClients connectorClients;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final RecordingsManager recordingsManager;
    private final OriginContext originContext;
    private final ProjectScope scope;

    public ConnectorProjectManager(
            MicroscopeJeffreyDirs jeffreyDirs,
            ProjectInfo projectInfo,
            RepositoryStatistics repositoryStatistics,
            ConnectorClients connectorClients,
            ProfilesManager.Factory profilesManagerFactory,
            RecordingsManager recordingsManager,
            OriginContext originContext) {

        this.jeffreyDirs = jeffreyDirs;
        this.projectInfo = projectInfo;
        this.repositoryStatistics = repositoryStatistics;
        this.connectorClients = connectorClients;
        this.profilesManagerFactory = profilesManagerFactory;
        this.recordingsManager = recordingsManager;
        this.originContext = originContext;
        this.scope = ProjectScope.of(projectInfo);
    }

    @Override
    public ProjectInfo info() {
        return projectInfo;
    }

    @Override
    public ProfilesManager profilesManager() {
        return profilesManagerFactory.apply(projectInfo);
    }

    @Override
    public RecordingsDownloadManager recordingsDownloadManager() {
        return new ConnectorRecordingsDownloadManager(
                jeffreyDirs::newTempDir,
                connectorClients.files(),
                connectorClients.repository(),
                recordingsManager,
                originContext,
                scope);
    }

    @Override
    public RepositoryManager repositoryManager() {
        return new ConnectorRepositoryManager(
                jeffreyDirs::newTempDir,
                scope,
                repositoryStatistics,
                connectorClients.repository(),
                connectorClients.files());
    }

    @Override
    public ConnectorInstancesManager instancesManager() {
        return new ConnectorInstancesManager(scope, connectorClients.instances());
    }

    @Override
    public void delete() {
        connectorClients.projects().deleteProject(scope);
    }
}
