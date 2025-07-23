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

package pbouda.jeffrey.project.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.ExternalProjectLink;
import pbouda.jeffrey.common.pipeline.Stage;
import pbouda.jeffrey.manager.ProjectManager;

import java.util.Objects;

public class AddExternalProjectLinkStage implements Stage<CreateProjectContext> {

    private static final Logger LOG = LoggerFactory.getLogger(AddExternalProjectLinkStage.class);

    private final ProjectManager.Factory projectManagerFactory;

    public AddExternalProjectLinkStage(ProjectManager.Factory projectManagerFactory) {
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public CreateProjectContext execute(CreateProjectContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(context.projectInfo(), "Project needs to be already set");

        String projectId = context.projectInfo().id();

        if (context.externalProjectLink() == null) {
            LOG.info("No external project link provided: project_id={}", projectId);
            return context;
        }

        ProjectManager projectManager = projectManagerFactory.apply(context.projectInfo());

        ExternalProjectLink projectLink = projectManager.createProjectExternalLink(context.externalProjectLink());
        LOG.info("Adding external project link: project_id={} external_component_id={} external_component_type={}",
                projectId, projectLink.externalComponentId(), projectLink.externalComponentType());

        return context.withProjectInfo(
                context.projectInfo().withExternalLink(projectLink));
    }
}
