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

package pbouda.jeffrey.platform.project.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.pipeline.Stage;
import pbouda.jeffrey.platform.project.template.ProjectTemplate;
import pbouda.jeffrey.platform.project.template.ProjectTemplatesLoader;
import pbouda.jeffrey.common.model.job.JobInfo;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.platform.scheduler.JobDefinition;
import pbouda.jeffrey.platform.scheduler.JobDefinitionLoader;

import java.util.Objects;
import java.util.Optional;

public class AddProjectJobsStage implements Stage<CreateProjectContext> {

    private static final Logger LOG = LoggerFactory.getLogger(AddProjectJobsStage.class);

    private final Repositories repositories;
    private final ProjectTemplatesLoader templatesLoader;
    private final JobDefinitionLoader jobDefinitionLoader;

    public AddProjectJobsStage(
            Repositories repositories,
            ProjectTemplatesLoader templatesLoader,
            JobDefinitionLoader jobDefinitionLoader) {

        this.repositories = repositories;
        this.templatesLoader = templatesLoader;
        this.jobDefinitionLoader = jobDefinitionLoader;
    }

    @Override
    public CreateProjectContext execute(CreateProjectContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(context.projectInfo(), "Project needs to be already set");

        String projectId = context.projectInfo().id();

        // No template to be applied
        if (context.createProject().templateId() == null) {
            return context;
        }

        Optional<ProjectTemplate> templateOpt = templatesLoader.load(context.createProject().templateId());
        if (templateOpt.isEmpty()) {
            return context;
        }

        ProjectTemplate template = templateOpt.get();

        SchedulerRepository schedulerRepository =
                repositories.newProjectSchedulerRepository(projectId);

        for (String jobDefinitionId : template.jobDefinitions()) {
            Optional<JobDefinition> jobDefinitionOpt = jobDefinitionLoader.load(jobDefinitionId);
            if (jobDefinitionOpt.isEmpty()) {
                LOG.warn("Job definition {} not found", jobDefinitionId);
                continue;
            }

            JobDefinition jobDefinition = jobDefinitionOpt.get();
            JobInfo jobInfo = new JobInfo(
                    IDGenerator.generate(), projectId, jobDefinition.type(), jobDefinition.params(), true);
            schedulerRepository.insert(jobInfo);

            LOG.info("Job added to project: job_definition_id={} job_type={} job_params={} project_id={}",
                    jobDefinitionId, jobInfo.jobType(), jobInfo.params(), projectId);
        }

        return context;
    }
}
