/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.project.HubProject;
import cafe.jeffrey.hub.core.project.HubProjects;
import cafe.jeffrey.hub.model.job.JobType;
import cafe.jeffrey.shared.common.measure.Measuring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Runs a job's work once for every project on the volume. One broken project — a vanished session
 * directory, a storage hiccup — must not abort the tick for the rest, so each visit runs isolated:
 * measured, logged, and its failure logged rather than thrown.
 */
final class ProjectFanOut {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectFanOut.class);

    private final HubProjects projects;
    private final JobType jobType;

    ProjectFanOut(HubProjects projects, JobType jobType) {
        this.projects = projects;
        this.jobType = jobType;
    }

    void forEachProject(Consumer<HubProject> work) {
        for (HubProject project : projects.all()) {
            visit(project, work);
        }
    }

    private void visit(HubProject project, Consumer<HubProject> work) {
        try {
            Duration elapsed = Measuring.r(() -> work.accept(project));
            LOG.debug("Job completed for a project: job_type={} elapsed_ms={} {}",
                    jobType, elapsed.toMillis(), project.logScope());
        } catch (Exception e) {
            LOG.error("Job failed for a project, continuing with the rest: job_type={} {}",
                    jobType, project.logScope(), e);
        }
    }
}
