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
