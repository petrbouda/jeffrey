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

package cafe.jeffrey.profile.perfcounters;

import cafe.jeffrey.microscope.model.ProfileInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** The perf-data snapshot a Perf Counters profile was built from. */
public interface PerfCountersManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, PerfCountersManager> {
    }

    /**
     * Reads the snapshot and keeps its counters with the profile, replacing any kept before.
     *
     * @return the figures the snapshot gives
     * @throws IllegalArgumentException when the file is not a perf-data file or holds no counters
     */
    PerfCountersOverview ingest(Path perfDataFile);

    boolean exists();

    /** Every counter, sorted by name; empty when no snapshot was kept. */
    List<PerfCounter> counters();

    /** The headline figures, or empty when no snapshot was kept. */
    Optional<PerfCountersOverview> overview();
}
