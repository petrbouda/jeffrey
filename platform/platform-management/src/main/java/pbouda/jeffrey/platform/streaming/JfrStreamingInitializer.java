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

package pbouda.jeffrey.platform.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.workspace.RepositorySessionInfo;

import java.util.List;

/**
 * Initializes JFR streaming consumers for existing active sessions on application startup.
 * Queries all unfinished sessions with streaming enabled and registers consumers for them.
 */
public class JfrStreamingInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(JfrStreamingInitializer.class);

    private final JfrStreamingConsumerManager consumerManager;
    private final CompositeWorkspacesManager workspacesManager;
    private final PlatformRepositories platformRepositories;

    public JfrStreamingInitializer(
            JfrStreamingConsumerManager consumerManager,
            CompositeWorkspacesManager workspacesManager,
            PlatformRepositories platformRepositories) {

        this.consumerManager = consumerManager;
        this.workspacesManager = workspacesManager;
        this.platformRepositories = platformRepositories;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        LOG.info("Initializing JFR streaming consumers for active sessions...");

        int count = 0;
        for (WorkspaceManager workspace : workspacesManager.findAll()) {
            for (ProjectManager project : workspace.projectsManager().findAll()) {
                count += initializeProjectConsumers(project);
            }
        }

        LOG.info("Initialized JFR streaming consumers: count={}", count);
    }

    private int initializeProjectConsumers(ProjectManager project) {
        ProjectRepositoryRepository repoRepository =
                platformRepositories.newProjectRepositoryRepository(project.info().id());

        List<RepositoryInfo> repos = repoRepository.getAll();
        if (repos.isEmpty()) {
            return 0;
        }

        RepositoryInfo repositoryInfo = repos.getFirst();
        List<RepositorySessionInfo> unfinishedSessions = repoRepository.findUnfinishedSessions();

        int count = 0;
        for (RepositorySessionInfo session : unfinishedSessions) {
            if (session.streamingEnabled()) {
                consumerManager.registerConsumer(repositoryInfo, session);
                count++;
            }
        }

        return count;
    }
}
