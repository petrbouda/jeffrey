/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.api.repository;

import pbouda.jeffrey.shared.model.ProfileInfo;

import java.util.Optional;

public interface ProfileRepository {

    /**
     * Find a single profile by its ID.
     *
     * @return the profile if it exists, otherwise an empty optional
     */
    Optional<ProfileInfo> find();

    /**
     * Newly created Profile is disabled by default. We need to explicitly call to enabled it after all
     * post-creation activities (caching etc.). After enabling, the profile is ready to be used by the system.
     */
    void enableProfile();

    /**
     * Update the profile name.
     *
     * @param name the new name for the profile
     * @return the updated profile info
     */
    ProfileInfo update(String name);

    /**
     * Delete the profile.
     */
    void delete();
}
