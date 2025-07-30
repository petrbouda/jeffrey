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
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.common.model.job.JobTypeScope;
import pbouda.jeffrey.scheduler.job.model.SynchronizationMode;

import java.nio.file.Path;
import java.util.Map;

public record ProjectsSynchronizerJobDescriptor(
        Path watchedFolder,
        String templateId,
        SynchronizationMode syncMode
) implements JobDescriptor<ProjectsSynchronizerJobDescriptor> {

    private static final String PARAM_WATCH_FOLDER = "watchedFolder";
    private static final String PARAM_SYNC_MODE = "syncMode";
    private static final String PARAM_TEMPLATE_ID = "templateId";

    @Override
    public Map<String, String> params() {
        return Map.of(
                PARAM_WATCH_FOLDER, watchedFolder.toString(),
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

    public static ProjectsSynchronizerJobDescriptor of(Map<String, String> params) {
        String watchFolderStr = JobDescriptorUtils.resolveParameter(params, PARAM_WATCH_FOLDER);
        String syncModeStr = JobDescriptorUtils.resolveParameter(params, PARAM_SYNC_MODE);
        String templateId = JobDescriptorUtils.resolveParameter(params, PARAM_TEMPLATE_ID);
        return of(watchFolderStr, syncModeStr, templateId);
    }

    public static ProjectsSynchronizerJobDescriptor of(PropertyResolver properties) {
        String watchedFolder = properties.getRequiredProperty("jeffrey.job.projects-synchronizer.watched-folders");
        String templateId = properties.getRequiredProperty("jeffrey.job.projects-synchronizer.templateId");
        String syncType = properties.getRequiredProperty("jeffrey.job.projects-synchronizer.sync-type");
        return of(watchedFolder, syncType, templateId);
    }

    private static ProjectsSynchronizerJobDescriptor of(String watchedFolder, String syncMode, String templateId) {
        return new ProjectsSynchronizerJobDescriptor(
                Path.of(watchedFolder), templateId, SynchronizationMode.valueOf(syncMode));
    }
}
