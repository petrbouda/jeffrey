/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.hub.core.scheduler;

/**
 * A {@link Job} that an operator may also run on demand from the UI.
 *
 * <p>Implementing this is the whole opt-in: the scheduler reports the capability, the REST layer
 * exposes the run, and the jobs table renders a control for it. Nothing anywhere names an
 * individual job, so a future job becomes triggerable by implementing this interface and nothing
 * else.</p>
 *
 * <p>The manual run is deliberately a separate method from {@link Job#execute}. A job's scheduled
 * tick and what an operator wants on demand are not always the same work — the workspace
 * reconciler ticks over the pending index but walks the entire tree when asked by hand.</p>
 */
public interface ManuallyTriggerable {

    /**
     * Runs the job's manual variant to completion and describes what it did in one line, phrased
     * for the person who pressed the button ("3 projects, 11 sessions"). The caller shows this
     * verbatim and never interprets it, so each job stays free to report in its own terms.
     *
     * <p>Runs on the caller's request thread: implementations are expected to finish in seconds.</p>
     */
    String runManually();
}
