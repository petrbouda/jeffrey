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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettings;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettingsResolver;
import cafe.jeffrey.shared.common.model.ProfileInfo;

/**
 * The profile's Advisor configuration — the source folder it reads. Keyed by the profile, so it works
 * the same for a project profile and a locally-uploaded Quick Analysis recording.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/advisor/settings")
public class AdvisorSettingsController {

    /**
     * @param sourcePath absolute path to the working copy on the machine running Microscope
     * @param configured true when a source folder is set, so the UI can gate Generate without
     *                   re-deriving the rule
     */
    public record AdvisorSettingsResponse(
            String sourcePath,
            boolean configured) {

        static AdvisorSettingsResponse from(AdvisorSettings settings) {
            return new AdvisorSettingsResponse(settings.sourcePath(), settings.hasSourcePath());
        }
    }

    public record AdvisorSettingsRequest(
            String sourcePath) {
    }

    private final ProfileManagerResolver resolver;
    private final AdvisorSettingsResolver settingsResolver;

    public AdvisorSettingsController(
            ProfileManagerResolver resolver, AdvisorSettingsResolver settingsResolver) {

        this.resolver = resolver;
        this.settingsResolver = settingsResolver;
    }

    @GetMapping
    public AdvisorSettingsResponse get(@PathVariable("profileId") String profileId) {
        ProfileInfo profile = resolver.resolve(profileId).info();
        return AdvisorSettingsResponse.from(settingsResolver.resolve(profile));
    }

    @PutMapping
    public AdvisorSettingsResponse save(
            @PathVariable("profileId") String profileId,
            @RequestBody AdvisorSettingsRequest request) {

        ProfileInfo profile = resolver.resolve(profileId).info();
        AdvisorSettings current = settingsResolver.resolve(profile);
        AdvisorSettings updated = merge(current, request);
        return AdvisorSettingsResponse.from(settingsResolver.save(profile.id(), updated));
    }

    /**
     * Applies the source folder the request carried, leaving the rest of the resolved settings — the
     * global prune threshold — as they are. A request that omits the field leaves the folder unchanged.
     */
    private static AdvisorSettings merge(AdvisorSettings current, AdvisorSettingsRequest request) {
        if (request == null || request.sourcePath() == null) {
            return current;
        }
        return new AdvisorSettings(request.sourcePath(), current.pruneThresholdPct());
    }
}
