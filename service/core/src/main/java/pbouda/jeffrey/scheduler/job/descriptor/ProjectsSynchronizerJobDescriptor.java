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
import pbouda.jeffrey.common.model.job.JobTypeScope;
import pbouda.jeffrey.scheduler.job.model.SynchronizationMode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record ProjectsSynchronizerJobDescriptor(
        Path repositoriesDir,
        String templateId,
        SynchronizationMode syncMode
) implements JobDescriptor<ProjectsSynchronizerJobDescriptor> {

    private static final String PARAM_REPOSITORIES_DIR = "repositoriesDir";
    private static final String PARAM_SYNC_MODE = "syncMode";
    private static final String PARAM_TEMPLATE_ID = "templateId";

    @Override
    public Map<String, String> params() {
        return Map.of(
                PARAM_REPOSITORIES_DIR, repositoriesDir.toString(),
                PARAM_SYNC_MODE, syncMode.name(),
                PARAM_TEMPLATE_ID, templateId);
    }

    @Override
    public JobType type() {
        return JobType.PROJECTS_SYNCHRONIZER;
    }

    @Override
    public JobTypeScope scope() {
        return JobTypeScope.GLOBAL;
    }

    public static ProjectsSynchronizerJobDescriptor of(HomeDirs homeDirs, PropertyResolver properties) {
        String repositoriesDir = properties.getProperty("jeffrey.job.projects-synchronizer.repositories-dir");
        String templateId = properties.getRequiredProperty("jeffrey.job.projects-synchronizer.template-id");
        String syncType = properties.getRequiredProperty("jeffrey.job.projects-synchronizer.sync-type");

        Map<String, String> params = new HashMap<>();
        params.put(PARAM_REPOSITORIES_DIR, repositoriesDir);
        params.put(PARAM_SYNC_MODE, syncType);
        params.put(PARAM_TEMPLATE_ID, templateId);

        return of(homeDirs, params);
    }

    public static ProjectsSynchronizerJobDescriptor of(HomeDirs homeDirs, Map<String, String> params) {
        // Ensure that the repositories directory is set, defaulting to the home directory's repositories path
        params.putIfAbsent(PARAM_REPOSITORIES_DIR, homeDirs.repositories().toString());

        String repositoriesDirStr = JobDescriptorUtils.resolveParameter(params, PARAM_REPOSITORIES_DIR);
        String syncModeStr = JobDescriptorUtils.resolveParameter(params, PARAM_SYNC_MODE);
        String templateId = JobDescriptorUtils.resolveParameter(params, PARAM_TEMPLATE_ID);
        return of(repositoriesDirStr, syncModeStr, templateId);
    }

    private static ProjectsSynchronizerJobDescriptor of(String repositoriesDir, String syncMode, String templateId) {
        return new ProjectsSynchronizerJobDescriptor(
                Path.of(repositoriesDir), templateId, SynchronizationMode.valueOf(syncMode));
    }
}
