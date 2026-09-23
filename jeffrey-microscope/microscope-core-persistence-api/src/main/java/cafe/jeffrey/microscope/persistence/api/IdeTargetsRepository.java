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

package cafe.jeffrey.microscope.persistence.api;

import java.util.Optional;

/**
 * Where a profile's IDE link is kept between runs.
 *
 * <p>Without this the link lives only in memory, so every restart of Jeffrey silently unlinks every
 * profile and the reader discovers it by clicking a button that no longer works.
 *
 * <p>One row per profile: a profile is about one checkout, and re-linking replaces rather than adds.
 */
public interface IdeTargetsRepository {

    /**
     * Records the window chosen for a profile, replacing any earlier choice.
     */
    void save(String profileId, IdeTargetLink link);

    /**
     * The window chosen for a profile, or empty when none was ever chosen — or when the reader
     * disconnected it.
     */
    Optional<IdeTargetLink> find(String profileId);

    /**
     * Forgets a profile's window. Called when the reader disconnects it, so that a link they removed
     * does not come back after a restart.
     */
    void delete(String profileId);
}
