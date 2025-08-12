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

package pbouda.jeffrey.scheduler.job.descriptor;

import org.springframework.core.env.PropertyResolver;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.scheduler.job.model.SynchronizationMode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record ProjectsSynchronizerJobDescriptor(
        Path workspacesDir,
        String templateId,
        SynchronizationMode syncMode
) implements JobDescriptor<ProjectsSynchronizerJobDescriptor> {

    private static final String PARAM_WORKSPACES_DIR = "workspacesDir";
    private static final String PARAM_SYNC_MODE = "syncMode";
    private static final String PARAM_TEMPLATE_ID = "templateId";

    @Override
    public Map<String, String> params() {
        return Map.of(
                PARAM_WORKSPACES_DIR, workspacesDir.toString(),
                PARAM_SYNC_MODE, syncMode.name(),
                PARAM_TEMPLATE_ID, templateId);
    }

    @Override
    public JobType type() {
        return JobType.PROJECTS_SYNCHRONIZER;
    }

    public static ProjectsSynchronizerJobDescriptor of(HomeDirs homeDirs, PropertyResolver properties) {
        String workspacesDir = properties.getProperty("jeffrey.job.projects-synchronizer.workspaces-dir");
        String templateId = properties.getRequiredProperty("jeffrey.job.projects-synchronizer.template-id");
        String syncType = properties.getRequiredProperty("jeffrey.job.projects-synchronizer.sync-type");

        Map<String, String> params = new HashMap<>();
        params.put(PARAM_WORKSPACES_DIR, workspacesDir);
        params.put(PARAM_SYNC_MODE, syncType);
        params.put(PARAM_TEMPLATE_ID, templateId);

        return of(homeDirs, params);
    }

    public static ProjectsSynchronizerJobDescriptor of(HomeDirs homeDirs, Map<String, String> params) {
        // Ensure that the repositories directory is set, defaulting to the home directory's repositories path
        params.putIfAbsent(PARAM_WORKSPACES_DIR, homeDirs.workspaces().toString());

        String workspacesDirStr = JobDescriptorUtils.resolveParameter(params, PARAM_WORKSPACES_DIR);
        String syncModeStr = JobDescriptorUtils.resolveParameter(params, PARAM_SYNC_MODE);
        String templateId = JobDescriptorUtils.resolveParameter(params, PARAM_TEMPLATE_ID);
        return of(workspacesDirStr, syncModeStr, templateId);
    }

    private static ProjectsSynchronizerJobDescriptor of(String workspacesDir, String syncMode, String templateId) {
        return new ProjectsSynchronizerJobDescriptor(
                Path.of(workspacesDir), templateId, SynchronizationMode.valueOf(syncMode));
    }
}
