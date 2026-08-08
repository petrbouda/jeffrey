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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.manager.ProfileRecreationManager;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}")
public class ProfileController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileManagerResolver resolver;
    private final ProfileRecreationManager recreationManager;

    public ProfileController(ProfileManagerResolver resolver, ProfileRecreationManager recreationManager) {
        this.resolver = resolver;
        this.recreationManager = recreationManager;
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

    /**
     * Deletes an outdated profile and re-analyzes its original recording. The replacement
     * profile appears in the profile list with the regular "analyzing" state.
     */
    @PostMapping("/recreate")
    public ResponseEntity<Void> recreateProfile(@PathVariable("profileId") String profileId) {
        LOG.info("Recreating profile from its recording: profileId={}", profileId);
        recreationManager.recreate(profileId);
        return ResponseEntity.accepted().build();
    }

    public record UpdateProfile(String name) {
    }
}
