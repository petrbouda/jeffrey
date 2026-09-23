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

import cafe.jeffrey.hub.client.environment.SessionEnvironmentParser;
import cafe.jeffrey.hub.client.environment.SessionEnvironmentReader;
import cafe.jeffrey.hub.client.manager.RemoteInstancesManager;
import cafe.jeffrey.hub.client.manager.RemoteRepositoryManager;
import cafe.jeffrey.hub.client.manager.RepositoryManager;

import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.microscope.core.manager.*;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.recordings.core.OriginContext;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.recordings.core.RemoteRecordingsDownloadManager;
import cafe.jeffrey.microscope.model.ProjectInfo;

public class RemoteProjectManager implements ProjectManager {

    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final DetailedProjectInfo detailedProjectInfo;
    private final HubClients remoteClients;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final RecordingsManager recordingsManager;
    private final OriginContext originContext;

    public RemoteProjectManager(
            MicroscopeJeffreyDirs jeffreyDirs,
            DetailedProjectInfo detailedProjectInfo,
            HubClients remoteClients,
            ProfilesManager.Factory profilesManagerFactory,
            RecordingsManager recordingsManager,
            OriginContext originContext) {

        this.jeffreyDirs = jeffreyDirs;
        this.detailedProjectInfo = detailedProjectInfo;
        this.remoteClients = remoteClients;
        this.profilesManagerFactory = profilesManagerFactory;
        this.recordingsManager = recordingsManager;
        this.originContext = originContext;
    }

    @Override
    public ProjectInfo info() {
        return detailedProjectInfo.projectInfo();
    }

    @Override
    public DetailedProjectInfo detailedInfo() {
        return detailedProjectInfo;
    }

    @Override
    public ProfilesManager profilesManager() {
        return profilesManagerFactory.apply(detailedProjectInfo.projectInfo());
    }

    @Override
    public RecordingsDownloadManager recordingsDownloadManager() {
        return new RemoteRecordingsDownloadManager(
                jeffreyDirs::newTempDir,
                remoteClients.files(),
                remoteClients.repository(),
                recordingsManager,
                originContext,
                detailedProjectInfo.projectInfo().name());
    }

    @Override
    public RepositoryManager repositoryManager() {
        return new RemoteRepositoryManager(
                jeffreyDirs::newTempDir,
                detailedProjectInfo.projectInfo(),
                remoteClients.repository(),
                remoteClients.files());
    }

    @Override
    public RemoteInstancesManager instancesManager() {
        return new RemoteInstancesManager(
                detailedProjectInfo.projectInfo(),
                remoteClients.instances(),
                new SessionEnvironmentReader(
                        repositoryManager(),
                        new SessionEnvironmentParser(jeffreyDirs)));
    }

    @Override
    public void updateName(String name) {
        throw new UnsupportedOperationException("Renaming remote projects is not supported");
    }

    @Override
    public void restore() {
        remoteClients.projects().restoreProject(
                detailedProjectInfo.projectInfo().id());
    }

    @Override
    public void delete() {
        remoteClients.projects().deleteProject(
                detailedProjectInfo.projectInfo().id());
    }
}
