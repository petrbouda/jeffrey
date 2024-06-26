/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import pbouda.jeffrey.controller.model.CreateProfileRequest;
import pbouda.jeffrey.controller.model.DeleteProfileRequest;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/profiles")
public class ProfileController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileController.class);

    private final ProfilesManager profilesManager;

    public ProfileController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @GetMapping
    public List<ProfileInfo> profiles() {
        return profilesManager.allProfiles().stream()
                .map(ProfileManager::info)
                .sorted(Comparator.comparing(ProfileInfo::createdAt).reversed())
                .toList();
    }

    @PostMapping
    public ProfileInfo createProfile(@RequestBody CreateProfileRequest request) {
        return profilesManager.createProfile(Path.of(request.recordingPath())).info();
    }

    @PostMapping("/delete")
    public void deleteProfile(@RequestBody DeleteProfileRequest request) {
        for (String profileId : request.profileIds()) {
            profilesManager.deleteProfile(profileId);
            LOG.info("Deleted profile: profile_id={}", profileId);
        }
    }
}
