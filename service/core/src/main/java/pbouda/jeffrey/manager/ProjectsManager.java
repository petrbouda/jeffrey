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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.ExternalProjectLink;
import pbouda.jeffrey.project.ProjectTemplate;
import pbouda.jeffrey.project.TemplateTarget;

import java.util.List;
import java.util.Optional;

public interface ProjectsManager {

    ProjectManager create(String name, String templateId, ExternalProjectLink externalProjectLink);

    List<? extends ProjectManager> allProjects();

    Optional<ProjectManager> project(String projectId);

    List<ProjectTemplate> templates(TemplateTarget templateTarget);

}
