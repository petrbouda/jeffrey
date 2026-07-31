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
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;

/**
 * The project's Advisor configuration, reached through a profile because that is where the user is
 * standing when they need it.
 *
 * <p>The settings belong to the project — every profile of a service shares one working copy — but
 * Microscope keeps no local project row to address, so the profile supplies the workspace/project pair.
 * A profile with no project (Quick Analysis) has nowhere to store this, and says so rather than writing
 * a row nothing will ever read again.</p>
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/advisor/settings")
public class AdvisorSettingsController {

    /**
     * @param sourcePath            absolute path to the working copy on the machine running Microscope
     * @param configured            true when a source folder is set, so the UI can gate Generate without
     *                              re-deriving the rule
     * @param pruneThresholdPct     how much of the call tree reaches the model
     * @param regressionThresholdPp how far a frame must move to count as a regression
     * @param compileCommand        blank means the compile check reports as skipped
     * @param testCommand           blank means the test check reports as skipped
     */
    public record AdvisorSettingsResponse(
            String sourcePath,
            boolean configured,
            double pruneThresholdPct,
            double regressionThresholdPp,
            String compileCommand,
            String testCommand) {

        static AdvisorSettingsResponse from(AdvisorSettings settings) {
            return new AdvisorSettingsResponse(
                    settings.sourcePath(),
                    settings.hasSourcePath(),
                    settings.pruneThresholdPct(),
                    settings.regressionThresholdPp(),
                    settings.compileCommand(),
                    settings.testCommand());
        }
    }

    public record AdvisorSettingsRequest(
            String sourcePath,
            Double pruneThresholdPct,
            Double regressionThresholdPp,
            String compileCommand,
            String testCommand) {
    }

    private static final String NO_PROJECT =
            "This profile does not belong to a project, so it has no source folder to configure. "
                    + "Open a profile from a project to use the Advisor.";

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
        if (profile.projectId() == null || profile.projectId().isBlank()) {
            throw Exceptions.invalidRequest(NO_PROJECT);
        }

        AdvisorSettings current = settingsResolver.resolve(profile);
        AdvisorSettings updated = merge(current, request);
        return AdvisorSettingsResponse.from(
                settingsResolver.save(profile.workspaceId(), profile.projectId(), updated));
    }

    /**
     * Applies only the fields the request actually carried. A partial edit — the settings page saving
     * just the source folder — must not blank the thresholds it never showed.
     *
     * <p>The record's own validation rejects out-of-range values, and it throws
     * {@link IllegalArgumentException} because it is domain code; that is mapped to a client error here,
     * at the boundary where framework concerns belong.</p>
     */
    private static AdvisorSettings merge(AdvisorSettings current, AdvisorSettingsRequest request) {
        if (request == null) {
            return current;
        }
        try {
            return new AdvisorSettings(
                    request.sourcePath() == null ? current.sourcePath() : request.sourcePath(),
                    request.pruneThresholdPct() == null
                            ? current.pruneThresholdPct() : request.pruneThresholdPct(),
                    request.regressionThresholdPp() == null
                            ? current.regressionThresholdPp() : request.regressionThresholdPp(),
                    request.compileCommand() == null ? current.compileCommand() : request.compileCommand(),
                    request.testCommand() == null ? current.testCommand() : request.testCommand());
        } catch (IllegalArgumentException e) {
            throw Exceptions.invalidRequest(e.getMessage());
        }
    }
}
