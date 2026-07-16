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

package cafe.jeffrey.profile.manager.memory;

import cafe.jeffrey.profile.manager.model.leak.LeakCandidate;
import cafe.jeffrey.profile.manager.model.leak.LeakOverview;
import cafe.jeffrey.shared.common.model.ProfileInfo;

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
