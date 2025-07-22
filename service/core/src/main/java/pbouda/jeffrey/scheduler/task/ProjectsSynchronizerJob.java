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
import pbouda.jeffrey.common.model.ExternalComponentId;
import pbouda.jeffrey.common.model.ExternalProjectLink;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.provider.api.model.job.JobInfo;
import pbouda.jeffrey.provider.api.model.job.JobType;

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

    public ProjectsSynchronizerJob(
            ProjectsManager projectsManager,
            SchedulerManager schedulerManager,
            Duration period) {

        super(schedulerManager, JOB_TYPE, period);
        this.projectsManager = projectsManager;
    }

    @Override
    protected void execute(JobInfo jobInfo) {
        String watchFolderStr = resolveParameter(jobInfo.params(), PARAM_WATCH_FOLDER);
        String syncType = resolveParameter(jobInfo.params(), PARAM_SYNC_TYPE);
        String templateId = resolveParameter(jobInfo.params(), PARAM_TEMPLATE_ID);

        LOG.info("Executing ProjectsSynchronizerJob with watchFolder: {}, syncType: {}, templateId: {}",
                watchFolderStr, syncType, templateId);

        Path watchedFolder = Path.of(watchFolderStr);

        if (Files.notExists(watchedFolder)) {
            throw new IllegalArgumentException(
                    "The watchedFolder for synchronizing projects does not exist: " + watchedFolder);
        }
        if (!Files.isDirectory(watchedFolder)) {
            throw new IllegalArgumentException("The watchedFolder is not a directory: " + watchedFolder);
        }

        // All folders in watched folder, a new project needs to be created if there is any new folder
        List<Path> currentFolders = FileSystemUtils.allDirectoriesInDirectory(watchedFolder);

        // All current projects
        List<? extends ProjectManager> currentProjects = projectsManager.allProjects();

        for (Path folder : currentFolders) {
            // Has the folder been already a project?
            boolean projectNotExists = currentProjects.stream()
                    .map(project -> project.info().name())
                    .noneMatch(name -> name.equals(folder.getFileName().toString()));

            if (projectNotExists) {
                String newProjectName = folder.getFileName().toString();
                projectsManager.create(newProjectName, templateId, ExternalProjectLink.byProjectsSynchronizer(folder));
                LOG.info("ProjectsSynchronizer Job created a new project: name={} template_id={}",
                        newProjectName, templateId);
            }
        }
    }

    private static String resolveParameter(Map<String, String> params, String name) {
        String value = params.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
