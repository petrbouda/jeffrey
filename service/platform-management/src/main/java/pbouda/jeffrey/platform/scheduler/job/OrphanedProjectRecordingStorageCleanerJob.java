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

package pbouda.jeffrey.platform.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.OrphanedProjectRecordingStorageCleanerJobDescriptor;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scheduler job that removes orphaned projects from recording storage.
 * An orphaned project is one that exists in storage but has been deleted from the database.
 */
public class OrphanedProjectRecordingStorageCleanerJob
        extends WorkspaceJob<OrphanedProjectRecordingStorageCleanerJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(OrphanedProjectRecordingStorageCleanerJob.class);

    private final RecordingStorage recordingStorage;
    private final Duration period;

    public OrphanedProjectRecordingStorageCleanerJob(
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            JobDescriptorFactory jobDescriptorFactory,
            RecordingStorage recordingStorage,
            Duration period) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.recordingStorage = recordingStorage;
        this.period = period;
    }

    @Override
    protected void executeOnWorkspace(
            WorkspaceManager workspaceManager,
            OrphanedProjectRecordingStorageCleanerJobDescriptor jobDescriptor) {

        String workspaceId = workspaceManager.resolveInfo().id();

        // Get all project IDs from recording storage
        List<String> projectsInStorage = recordingStorage.findAllProjects();
        if (projectsInStorage.isEmpty()) {
            LOG.debug("No projects in recording storage: workspace={}", workspaceId);
            return;
        }

        // Get all project IDs from database
        Set<String> projectsInDatabase = workspaceManager.projectsManager().findAll().stream()
                .map(pm -> pm.info().id())
                .collect(Collectors.toSet());

        // Find orphaned projects (in storage but not in database)
        List<String> orphanedProjects = new ArrayList<>(projectsInStorage);
        orphanedProjects.removeAll(projectsInDatabase);

        if (orphanedProjects.isEmpty()) {
            LOG.debug("No orphaned projects found: workspace={}", workspaceId);
            return;
        }

        // Delete orphaned projects from storage
        for (String projectId : orphanedProjects) {
            try {
                recordingStorage.projectRecordingStorage(projectId).delete();
                LOG.info("Removed orphaned project from recording storage: project_id={} workspace={}",
                        projectId, workspaceId);
            } catch (Exception e) {
                LOG.error("Failed to delete orphaned project from storage: project_id={} workspace={}",
                        projectId, workspaceId, e);
            }
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.ORPHANED_PROJECT_RECORDING_STORAGE_CLEANER;
    }
}
