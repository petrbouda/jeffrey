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

package pbouda.jeffrey.platform.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.util.List;
import java.util.Optional;

public class JobDefinitionLoader {

    private static final TypeReference<List<JobDefinition>> JOB_DEFINITIONS_TYPE =
            new TypeReference<List<JobDefinition>>() {
            };

    private final String jobDefinitionPath;

    public JobDefinitionLoader(String jobDefinitionPath) {
        this.jobDefinitionPath = jobDefinitionPath;
    }

    public Optional<JobDefinition> load(String jobDefinitionId) {
        List<JobDefinition> jobDefinitions = FileSystemUtils.readJson(jobDefinitionPath, JOB_DEFINITIONS_TYPE);
        return jobDefinitions.stream()
                .filter(def -> def.id().equals(jobDefinitionId))
                .findFirst();
    }
}
