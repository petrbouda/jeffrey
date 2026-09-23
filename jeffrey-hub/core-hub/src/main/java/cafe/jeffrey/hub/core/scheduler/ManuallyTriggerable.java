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
