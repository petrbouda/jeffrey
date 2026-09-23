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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.mcp.McpProfileContextCache;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.dto.response.ProfileDetailResponse;
import cafe.jeffrey.profile.manager.ProfileManager;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}")
public class ProfileController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileManagerResolver resolver;
    private final McpProfileContextCache contextCache;

    public ProfileController(ProfileManagerResolver resolver, McpProfileContextCache contextCache) {
        this.resolver = resolver;
        this.contextCache = contextCache;
    }

    @GetMapping
    public ProfileDetailResponse getProfileInfo(@PathVariable("profileId") String profileId) {
        ProfileManager pm = resolver.resolve(profileId);
        LOG.debug("Fetching profile info: profileId={}", pm.info().id());
        return ProfileDetailResponse.from(pm.info());
    }

    @PutMapping
    public ProfileDetailResponse updateProfile(
            @PathVariable("profileId") String profileId,
            @RequestBody UpdateProfile updateProfile) {
        ProfileManager pm = resolver.resolve(profileId);
        LOG.debug("Updating profile: profileId={} name={}", pm.info().id(), updateProfile.name());
        return ProfileDetailResponse.from(pm.updateName(updateProfile.name()));
    }

    @DeleteMapping
    public void deleteProfile(@PathVariable("profileId") String profileId) {
        ProfileManager pm = resolver.resolve(profileId);
        LOG.debug("Deleting profile: profileId={}", pm.info().id());
        contextCache.invalidate(profileId);
        try {
            pm.delete();
        } finally {
            // Narrows, rather than closes, the window in which an MCP call resolved the profile after
            // the first invalidation but before deletion removed it from storage. A resolve slow
            // enough to land after this one leaves a context pointing at deleted storage; it fails
            // honestly when used and goes on the next idle sweep, which is why narrowing is enough.
            contextCache.invalidate(profileId);
        }
    }

    public record UpdateProfile(String name) {
    }
}
