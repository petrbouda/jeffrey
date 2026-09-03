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

package cafe.jeffrey.microscope.mcp;

import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

/**
 * Resolves a profile from the core database alone: the row it was registered under, handed to the
 * {@link ProfileManager.Factory}. Every profile analysed in a Microscope installation has such a row,
 * whichever workspace it came from, so this is enough for a process that only reads.
 */
public final class LocalProfileResolver implements McpProfileResolver {

    private final MicroscopeCoreRepositories coreRepositories;
    private final ProfileManager.Factory profileManagerFactory;

    public LocalProfileResolver(
            MicroscopeCoreRepositories coreRepositories,
            ProfileManager.Factory profileManagerFactory) {
        this.coreRepositories = coreRepositories;
        this.profileManagerFactory = profileManagerFactory;
    }

    @Override
    public ProfileManager resolve(String profileId) {
        return coreRepositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory)
                .orElseThrow(() -> Exceptions.profileNotFound(profileId));
    }
}
