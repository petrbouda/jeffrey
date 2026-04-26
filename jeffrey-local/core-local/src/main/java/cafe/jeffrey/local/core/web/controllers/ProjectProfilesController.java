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

package cafe.jeffrey.local.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.manager.ProfilesManager;
import cafe.jeffrey.local.core.manager.project.ProjectManager;
import cafe.jeffrey.local.core.resources.request.CreateProfileRequest;
import cafe.jeffrey.local.core.resources.response.ProfileSummaryResponse;
import cafe.jeffrey.local.core.web.ProjectManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles")
public class ProjectProfilesController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectProfilesController.class);

    private final ProjectManagerResolver resolver;

    public ProjectProfilesController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<ProfileSummaryResponse> profiles(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ProfilesManager profilesManager = managerFor(workspaceId, projectId);
        var result = profilesManager.allProfiles().stream()
                .sorted(Comparator.comparing((ProfileManager pm) -> pm.info().createdAt()).reversed())
                .map(ProfileSummaryResponse::from)
                .toList();
        LOG.debug("Listed profiles: projectId={} count={}", projectId, result.size());
        return result;
    }

    @PostMapping
    public ResponseEntity<Void> createProfile(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestBody CreateProfileRequest request) {
        LOG.debug("Creating profile: recordingId={}", request.recordingId());
        managerFor(workspaceId, projectId).createProfile(request.recordingId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    private ProfilesManager managerFor(String workspaceId, String projectId) {
        ProjectManager pm = resolver.resolve(workspaceId, projectId).projectManager();
        return pm.profilesManager();
    }
}
