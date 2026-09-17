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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.shared.common.measure.Measuring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * A job that visits every workspace, or every project of every workspace, once per tick.
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
