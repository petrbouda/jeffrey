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

package pbouda.jeffrey.platform.scheduler.job.descriptor;

import org.springframework.core.env.PropertyResolver;
import pbouda.jeffrey.shared.common.model.job.JobType;

import java.util.HashMap;
import java.util.Map;

public record ProjectsSynchronizerJobDescriptor(
        String templateId
) implements JobDescriptor<ProjectsSynchronizerJobDescriptor> {

    private static final String DEFAULT_TEMPLATE_ID = "default-projects-synchronizer-template";
    private static final String PARAM_TEMPLATE_ID = "templateId";

    @Override
    public Map<String, String> params() {
        return Map.of(PARAM_TEMPLATE_ID, templateId);
    }

    @Override
    public JobType type() {
        return JobType.PROJECTS_SYNCHRONIZER;
    }

    public static ProjectsSynchronizerJobDescriptor of(PropertyResolver properties) {
        String templateId = properties.getProperty(
                "jeffrey.job.projects-synchronizer.template-id", DEFAULT_TEMPLATE_ID);

        Map<String, String> params = new HashMap<>();
        params.put(PARAM_TEMPLATE_ID, templateId);
        return of(params);
    }

    public static ProjectsSynchronizerJobDescriptor of(Map<String, String> params) {
        return of(JobDescriptorUtils.resolveString(params, PARAM_TEMPLATE_ID));
    }

    private static ProjectsSynchronizerJobDescriptor of(String templateId) {
        return new ProjectsSynchronizerJobDescriptor(templateId);
    }
}
