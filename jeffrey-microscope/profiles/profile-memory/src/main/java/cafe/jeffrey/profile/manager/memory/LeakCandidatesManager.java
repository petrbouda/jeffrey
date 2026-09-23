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

package cafe.jeffrey.profile.manager.memory;

import cafe.jeffrey.profile.manager.model.leak.LeakCandidate;
import cafe.jeffrey.profile.manager.model.leak.LeakOverview;
import cafe.jeffrey.microscope.model.ProfileInfo;

import java.util.List;
import java.util.function.Function;

/**
 * Memory-leak-candidate insight for a single profile, from {@code jdk.OldObjectSample} — the JFR
 * old-object sampler flags live objects that survived long enough to be leak suspects. The event is
 * off by default, so the page is empty-state-gated.
 */
public interface LeakCandidatesManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, LeakCandidatesManager> {
    }

    /**
     * Headline metrics: candidate count, largest/total size, oldest age.
     */
    LeakOverview overview();

    /**
     * Leak candidates ordered by descending object size.
     */
    List<LeakCandidate> candidates();
}
