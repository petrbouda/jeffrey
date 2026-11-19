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

package cafe.jeffrey.volumescheduler.scheduler;

import cafe.jeffrey.volumescheduler.volume.VolumeProject;
import cafe.jeffrey.volumescheduler.volume.VolumeProjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Runs a job's work once for every project on the volume. One broken project — a vanished session
 * directory, a storage hiccup — must not abort the run for the rest, so each visit runs isolated:
 * measured, logged, and its failure logged rather than thrown.
 */
public final class ProjectFanOut {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectFanOut.class);

    private final VolumeProjects projects;
    private final JobType jobType;

    public ProjectFanOut(VolumeProjects projects, JobType jobType) {
        this.projects = projects;
        this.jobType = jobType;
    }

    /** @return how many projects the work finished for; a project whose work failed is not counted */
    public int forEachProject(Consumer<VolumeProject> work) {
        int done = 0;
        for (VolumeProject project : projects.all()) {
            if (visit(project, work)) {
                done++;
            }
        }
        return done;
    }

    private boolean visit(VolumeProject project, Consumer<VolumeProject> work) {
        try {
            Duration elapsed = Measuring.r(() -> work.accept(project));
            LOG.debug("Job completed for a project: job_type={} elapsed_ms={} {}",
                    jobType.id(), elapsed.toMillis(), project.ref().logScope());
            return true;
        } catch (Exception e) {
            LOG.error("Job failed for a project, continuing with the rest: job_type={} {}",
                    jobType.id(), project.ref().logScope(), e);
            return false;
        }
    }
}
