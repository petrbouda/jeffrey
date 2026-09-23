/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.shared.common.measure.Measuring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * A job that visits every project of every workspace once per tick.
 * One broken unit — a vanished session directory, a storage hiccup — must not abort the
 * tick for the rest, so each visit runs isolated: measured, logged, and its failure logged
 * rather than thrown.
 */
abstract class FanOutJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(FanOutJob.class);

    protected final WorkspacesManager workspacesManager;

    protected FanOutJob(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @Override
    public ExecutorGroup executorGroup() {
        return ExecutorGroup.PROJECT_FAN_OUT;
    }

    /**
     * @param scope what is being visited, as structured log keys, e.g. {@code workspace_id=ws-1 project_id=p-1}
     */
    protected void visit(String scope, Runnable body) {
        String job = getClass().getSimpleName();
        try {
            Duration elapsed = Measuring.r(body);
            LOG.debug("Job completed: job={} elapsed_ms={} {}", job, elapsed.toMillis(), scope);
        } catch (Exception e) {
            LOG.error("Job failed for one unit, continuing with the rest: job={} {}", job, scope, e);
        }
    }
}
