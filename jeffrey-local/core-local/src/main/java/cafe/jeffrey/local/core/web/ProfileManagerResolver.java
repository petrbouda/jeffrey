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

package cafe.jeffrey.local.core.web;

import cafe.jeffrey.local.core.manager.qanalysis.QuickAnalysisManager;
import cafe.jeffrey.local.core.manager.server.RemoteServerManager;
import cafe.jeffrey.local.core.manager.server.RemoteServersManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.local.persistence.api.LocalCoreRepositories;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.Optional;

/**
 * Resolves a {@code profileId} to the underlying {@link ProfileManager}.
 * Checks the quick-analysis store first, then falls back to a direct DB
 * lookup against the local-core profile repository.
 *
 * <p>For workspace-scoped profiles, walks the connected remote servers in order
 * and returns the first hit — workspace IDs are server-generated UUIDs so a
 * given profile belongs to exactly one server.
 */
public class ProfileManagerResolver {

    private final RemoteServersManager remoteServersManager;
    private final QuickAnalysisManager quickAnalysisManager;
    private final LocalCoreRepositories localCoreRepositories;

    public ProfileManagerResolver(
            RemoteServersManager remoteServersManager,
            QuickAnalysisManager quickAnalysisManager,
            LocalCoreRepositories localCoreRepositories) {
        this.remoteServersManager = remoteServersManager;
        this.quickAnalysisManager = quickAnalysisManager;
        this.localCoreRepositories = localCoreRepositories;
    }

    public ProfileManager resolve(String profileId) {
        return find(profileId)
                .orElseThrow(() -> Exceptions.profileNotFound(profileId));
    }

    public Optional<ProfileManager> find(String profileId) {
        if (quickAnalysisManager != null) {
            Optional<ProfileManager> quickProfile = quickAnalysisManager.profile(profileId);
            if (quickProfile.isPresent()) {
                return quickProfile;
            }
        }

        Optional<ProfileInfo> profileInfoOpt = localCoreRepositories.newProfileRepository(profileId).find();
        if (profileInfoOpt.isEmpty()) {
            return Optional.empty();
        }

        ProfileInfo profileInfo = profileInfoOpt.get();
        for (RemoteServerManager server : remoteServersManager.findAll()) {
            Optional<WorkspaceManager> ws = server.workspace(profileInfo.workspaceId());
            if (ws.isEmpty()) {
                continue;
            }
            Optional<ProfileManager> resolved = ws.flatMap(w -> w.projectsManager().project(profileInfo.projectId()))
                    .flatMap(pm -> pm.profilesManager().profile(profileId));
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }
}
