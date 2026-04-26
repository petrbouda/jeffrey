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

package pbouda.jeffrey.local.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

@RequestMapping({
        "/api/internal/profiles/{profileId}",
        "/api/internal/quick-analysis/profiles/{profileId}",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{profileId}"
})
@ResponseBody
public class ProfileController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileManagerResolver resolver;

    public ProfileController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public ProfileInfo getProfileInfo(@PathVariable("profileId") String profileId) {
        ProfileManager pm = resolver.resolve(profileId);
        LOG.debug("Fetching profile info: profileId={}", pm.info().id());
        return pm.info();
    }

    @PutMapping
    public ProfileInfo updateProfile(
            @PathVariable("profileId") String profileId,
            @RequestBody UpdateProfile updateProfile) {
        ProfileManager pm = resolver.resolve(profileId);
        LOG.debug("Updating profile: profileId={} name={}", pm.info().id(), updateProfile.name());
        return pm.updateName(updateProfile.name());
    }

    @DeleteMapping
    public void deleteProfile(@PathVariable("profileId") String profileId) {
        ProfileManager pm = resolver.resolve(profileId);
        LOG.debug("Deleting profile: profileId={}", pm.info().id());
        pm.delete();
    }

    public record UpdateProfile(String name) {
    }
}
