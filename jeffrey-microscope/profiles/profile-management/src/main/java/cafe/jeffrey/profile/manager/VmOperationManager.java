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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStat;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointLatencyData;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOverview;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * VM-operation insight for a single profile: JVM-internal stop-the-world activity beyond GC from
 * {@code jdk.ExecuteVMOperation} plus time-to-safepoint from
 * {@code jdk.SafepointStateSynchronization}. Most of these events are threshold- or config-gated, so
 * consumers must handle empty results.
 */
public interface VmOperationManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, VmOperationManager> {
    }

    /**
     * Headline VM-operation / safepoint metrics for the VM Operations page.
     */
    VmOverview overview();

    /**
     * VM operations grouped by name, ordered by descending total duration.
     */
    List<VmOperationStat> vmOperations();

    /**
     * Safepoint pause time per second across the recording, in nanoseconds.
     */
    TimeseriesData pausesTimeline();

    /**
     * Time-to-safepoint per second, in nanoseconds (off by default — usually empty).
     */
    TimeseriesData timeToSafepointTimeline();

    /**
     * Which threads the JVM waited for on its way into safepoints, ranked.
     * <p>
     * The one question the rest of this page cannot answer: every other safepoint event is recorded
     * once per safepoint on a VM thread, so none of them knows whose slowness it measured.
     */
    SafepointLatencyData safepointOffenders();
}
