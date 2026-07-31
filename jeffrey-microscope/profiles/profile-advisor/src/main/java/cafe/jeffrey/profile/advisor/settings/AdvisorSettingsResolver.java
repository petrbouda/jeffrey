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

package cafe.jeffrey.profile.advisor.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.persistence.api.AdvisorSettingsRepository;
import cafe.jeffrey.microscope.persistence.api.AdvisorSettingsRow;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.time.Clock;

/**
 * Reads and writes a project's Advisor settings, falling back to the installation defaults.
 *
 * <p>A stored value that is out of range falls back <em>per field</em>. A row edited by hand, or
 * written before a validation rule existed, must not take the whole feature down — and discarding a
 * perfectly good prune threshold because its neighbour is negative would be exactly that.</p>
 */
public class AdvisorSettingsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorSettingsResolver.class);

    private final AdvisorSettingsRepository settingsRepository;
    private final Clock clock;

    public AdvisorSettingsResolver(AdvisorSettingsRepository settingsRepository, Clock clock) {
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    public AdvisorSettings resolve(ProfileInfo profile) {
        return resolve(profile.workspaceId(), profile.projectId());
    }

    /**
     * The settings for a project, or the defaults when the project has never been configured. A profile
     * with no project (Quick Analysis) always gets the defaults, which means it has no source folder and
     * the Advisor tells the user to open the project view instead.
     */
    public AdvisorSettings resolve(String workspaceId, String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return AdvisorSettings.defaults();
        }
        return settingsRepository.find(workspaceId, projectId)
                .map(AdvisorSettingsResolver::toSettings)
                .orElseGet(AdvisorSettings::defaults);
    }

    public AdvisorSettings save(String workspaceId, String projectId, AdvisorSettings settings) {
        settingsRepository.upsert(new AdvisorSettingsRow(
                workspaceId,
                projectId,
                settings.sourcePath(),
                settings.pruneThresholdPct(),
                settings.regressionThresholdPp(),
                settings.compileCommand(),
                settings.testCommand(),
                clock.instant()));

        LOG.info("Saved advisor settings: workspace_id={} project_id={} has_source_path={} "
                        + "prune_threshold_pct={} regression_threshold_pp={} has_compile_command={} has_test_command={}",
                workspaceId, projectId, settings.hasSourcePath(), settings.pruneThresholdPct(),
                settings.regressionThresholdPp(), !settings.compileCommand().isBlank(),
                !settings.testCommand().isBlank());
        return settings;
    }

    private static AdvisorSettings toSettings(AdvisorSettingsRow row) {
        double pruneThresholdPct = row.pruneThresholdPct();
        double regressionThresholdPp = row.regressionThresholdPp();

        if (pruneThresholdPct <= 0 || pruneThresholdPct >= 100) {
            LOG.warn("Ignoring out-of-range stored prune threshold: project_id={} prune_threshold_pct={}",
                    row.projectId(), pruneThresholdPct);
            pruneThresholdPct = AdvisorSettings.DEFAULT_PRUNE_THRESHOLD_PCT;
        }
        if (regressionThresholdPp < 0) {
            LOG.warn("Ignoring negative stored regression threshold: project_id={} regression_threshold_pp={}",
                    row.projectId(), regressionThresholdPp);
            regressionThresholdPp = AdvisorSettings.DEFAULT_REGRESSION_THRESHOLD_PP;
        }

        return new AdvisorSettings(
                row.sourcePath(), pruneThresholdPct, regressionThresholdPp,
                row.compileCommand(), row.testCommand());
    }
}
