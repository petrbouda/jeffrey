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

package pbouda.jeffrey.project;

import com.fasterxml.jackson.core.type.TypeReference;
import pbouda.jeffrey.FileUtils;

import java.util.List;
import java.util.Optional;

public class ProjectTemplatesLoader {

    private static final TypeReference<List<ProjectTemplate>> PROJECT_TEMPLATES_TYPE =
            new TypeReference<List<ProjectTemplate>>() {
            };

    private final String projectTemplatesPath;

    public ProjectTemplatesLoader(String projectTemplatesPath) {
        this.projectTemplatesPath = projectTemplatesPath;
    }

    public List<ProjectTemplate> loadAll() {
        return FileUtils.readJson(projectTemplatesPath, PROJECT_TEMPLATES_TYPE);
    }

    public Optional<ProjectTemplate> load(String templateId) {
        List<ProjectTemplate> projectTemplates = FileUtils.readJson(projectTemplatesPath, PROJECT_TEMPLATES_TYPE);
        return projectTemplates.stream()
                .filter(template -> template.id().equals(templateId))
                .findFirst();
    }
}
