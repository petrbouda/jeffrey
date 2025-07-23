/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.scheduler.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.provider.api.model.job.JobInfo;
import pbouda.jeffrey.provider.api.model.job.JobType;
import pbouda.jeffrey.scheduler.task.model.SynchronizationMode;
import pbouda.jeffrey.scheduler.task.sync.CreateOnlySynchronizationModeStrategy;
import pbouda.jeffrey.scheduler.task.sync.FullSyncSynchronizationModeStrategy;
import pbouda.jeffrey.scheduler.task.sync.SynchronizationModeStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ProjectsSynchronizerJob extends GlobalJob {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsSynchronizerJob.class);
    private static final JobType JOB_TYPE = JobType.PROJECTS_SYNCHRONIZER;

    private static final String PARAM_WATCH_FOLDER = "watchedFolder";
    private static final String PARAM_SYNC_TYPE = "syncType";
    private static final String PARAM_TEMPLATE_ID = "templateId";
    private final ProjectsManager projectsManager;
    private final List<SynchronizationModeStrategy> synchronizationModeStrategies;

    public ProjectsSynchronizerJob(
            ProjectsManager projectsManager,
            SchedulerManager schedulerManager,
            Duration period) {

        super(schedulerManager, JOB_TYPE, period);
        this.projectsManager = projectsManager;
        this.synchronizationModeStrategies = List.of(
                new CreateOnlySynchronizationModeStrategy(projectsManager),
                new FullSyncSynchronizationModeStrategy(projectsManager));
    }

    @Override
    protected void execute(JobInfo jobInfo) {
        String watchFolderStr = resolveParameter(jobInfo.params(), PARAM_WATCH_FOLDER);
        String syncTypeStr = resolveParameter(jobInfo.params(), PARAM_SYNC_TYPE);
        String templateId = resolveParameter(jobInfo.params(), PARAM_TEMPLATE_ID);

        LOG.debug("Executing ProjectsSynchronizerJob with watchFolder: {}, syncType: {}, templateId: {}",
                watchFolderStr, syncTypeStr, templateId);

        Path watchedFolder = Path.of(watchFolderStr);
        SynchronizationMode syncMode = SynchronizationMode.fromString(syncTypeStr);

        if (syncMode == null) {
            throw new IllegalArgumentException("Invalid syncType for ProjectsSynchronizer Job: " + syncTypeStr);
        }
        if (Files.notExists(watchedFolder)) {
            throw new IllegalArgumentException(
                    "The watchedFolder for synchronizing projects does not exist: " + watchedFolder);
        }
        if (!Files.isDirectory(watchedFolder)) {
            throw new IllegalArgumentException("The watchedFolder is not a directory: " + watchedFolder);
        }

        // All folders in watched folder, a new project needs to be created if there is any new folder
        List<Path> currentFolders = FileSystemUtils.allDirectoriesInDirectory(watchedFolder);

        SynchronizationModeStrategy synchronizationModeStrategy = synchronizationModeStrategies.stream()
                .filter(strategy -> strategy.synchronizationMode() == syncMode)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No synchronization mode strategy found for: " + syncMode));

        synchronizationModeStrategy.execute(currentFolders, projectsManager.allProjects(), templateId);
        LOG.info("ProjectsSynchronizer Job completed for watchFolder: {}, syncType: {}, templateId: {}",
                watchFolderStr, syncTypeStr, templateId);
    }

    private static String resolveParameter(Map<String, String> params, String name) {
        String value = params.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
