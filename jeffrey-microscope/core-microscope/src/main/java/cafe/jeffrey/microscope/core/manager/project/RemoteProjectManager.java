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

import cafe.jeffrey.microscope.remote.client.environment.SessionEnvironmentParser;
import cafe.jeffrey.microscope.remote.client.environment.SessionEnvironmentReader;
import cafe.jeffrey.microscope.remote.client.manager.RemoteInstancesManager;
import cafe.jeffrey.microscope.remote.client.manager.RemoteRepositoryManager;
import cafe.jeffrey.microscope.remote.client.manager.RepositoryManager;

import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.client.RemoteMappers;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.core.manager.*;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.recordings.core.OriginContext;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.recordings.core.RemoteRecordingsDownloadManager;
import cafe.jeffrey.microscope.model.ProjectInfo;

public class RemoteProjectManager implements ProjectManager {

    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final ProjectInfo projectInfo;
    private final RepositoryStatistics repositoryStatistics;
    private final RemoteClients remoteClients;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final RecordingsManager recordingsManager;
    private final OriginContext originContext;
    private final ProjectScope scope;

    public RemoteProjectManager(
            MicroscopeJeffreyDirs jeffreyDirs,
            ProjectInfo projectInfo,
            RepositoryStatistics repositoryStatistics,
            RemoteClients remoteClients,
            ProfilesManager.Factory profilesManagerFactory,
            RecordingsManager recordingsManager,
            OriginContext originContext) {

        this.jeffreyDirs = jeffreyDirs;
        this.projectInfo = projectInfo;
        this.repositoryStatistics = repositoryStatistics;
        this.remoteClients = remoteClients;
        this.profilesManagerFactory = profilesManagerFactory;
        this.recordingsManager = recordingsManager;
        this.originContext = originContext;
        this.scope = ProjectScope.of(projectInfo);
    }

    @Override
    public ProjectInfo info() {
        return projectInfo;
    }

    /**
     * Reads what the project's instances say of it, every time it is asked: finding the project
     * reads only its marker, so the requests that never show its status never pay for it.
     */
    @Override
    public DetailedProjectInfo detailedInfo() {
        return RemoteMappers.toDetailedProjectInfo(projectInfo, remoteClients.instances().activity(scope));
    }

    @Override
    public ProfilesManager profilesManager() {
        return profilesManagerFactory.apply(projectInfo);
    }

    @Override
    public RecordingsDownloadManager recordingsDownloadManager() {
        return new RemoteRecordingsDownloadManager(
                jeffreyDirs::newTempDir,
                remoteClients.files(),
                remoteClients.repository(),
                recordingsManager,
                originContext,
                scope);
    }

    @Override
    public RepositoryManager repositoryManager() {
        return new RemoteRepositoryManager(
                jeffreyDirs::newTempDir,
                scope,
                repositoryStatistics,
                remoteClients.repository(),
                remoteClients.files());
    }

    @Override
    public RemoteInstancesManager instancesManager() {
        return new RemoteInstancesManager(
                scope,
                remoteClients.instances(),
                remoteClients.repository(),
                new SessionEnvironmentReader(
                        repositoryManager(),
                        new SessionEnvironmentParser(jeffreyDirs)));
    }

    @Override
    public void updateName(String name) {
        throw new UnsupportedOperationException("Renaming remote projects is not supported");
    }

    @Override
    public void delete() {
        remoteClients.projects().deleteProject(scope);
    }
}
