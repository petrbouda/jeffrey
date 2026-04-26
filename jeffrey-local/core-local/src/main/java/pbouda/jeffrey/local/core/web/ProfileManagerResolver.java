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

package pbouda.jeffrey.local.core.web;

import jakarta.annotation.Nullable;
import pbouda.jeffrey.local.core.manager.qanalysis.QuickAnalysisManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.persistence.repository.LocalCoreRepositories;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

import java.util.Optional;

/**
 * Resolves a {@code profileId} to the underlying {@link ProfileManager} no
 * matter which URL root the request came in on (workspace/project chain,
 * top-level {@code /profiles/{profileId}}, or {@code /quick-analysis}).
 *
 * <p>Direct DB lookup avoids iterating all workspaces/projects, which would
 * trigger HTTP calls for remote workspaces.
 */
public class ProfileManagerResolver {

    private final WorkspacesManager workspacesManager;
    private final @Nullable QuickAnalysisManager quickAnalysisManager;
    private final LocalCoreRepositories localCoreRepositories;

    public ProfileManagerResolver(
            WorkspacesManager workspacesManager,
            @Nullable QuickAnalysisManager quickAnalysisManager,
            LocalCoreRepositories localCoreRepositories) {
        this.workspacesManager = workspacesManager;
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
        return workspacesManager.findById(profileInfo.workspaceId())
                .flatMap(ws -> ws.projectsManager().project(profileInfo.projectId()))
                .flatMap(pm -> pm.profilesManager().profile(profileId));
    }

    public ProfileManager resolveForWorkspaceProject(String workspaceId, String projectId, String profileId) {
        WorkspaceManager workspace = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));
        return workspace.projectsManager().project(projectId)
                .orElseThrow(() -> Exceptions.projectNotFound(projectId))
                .profilesManager().profile(profileId)
                .orElseThrow(() -> Exceptions.profileNotFound(profileId));
    }

    public ProfileManager resolveForQuickAnalysis(String profileId) {
        if (quickAnalysisManager == null) {
            throw Exceptions.profileNotFound(profileId);
        }
        return quickAnalysisManager.profile(profileId)
                .orElseThrow(() -> Exceptions.profileNotFound(profileId));
    }
}
