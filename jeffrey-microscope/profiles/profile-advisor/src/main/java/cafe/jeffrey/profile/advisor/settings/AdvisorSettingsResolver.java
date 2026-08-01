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
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.time.Clock;

/**
 * Resolves the settings an advisor run needs: the per-project source folder from the core database and
 * the installation-wide prune threshold from the global Advisor settings.
 *
 * <p>The prune threshold is read from the {@link SettingsStore} at resolve time rather than captured
 * once, so an edit on the settings page reaches the next run instead of the next restart. A stored
 * value that is out of range falls back to the default rather than taking the feature down — a value
 * edited by hand, or written before a validation rule existed, must not.</p>
 */
public class AdvisorSettingsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorSettingsResolver.class);

    private final AdvisorSettingsRepository settingsRepository;
    private final SettingsStore settingsStore;
    private final Clock clock;

    public AdvisorSettingsResolver(
            AdvisorSettingsRepository settingsRepository, SettingsStore settingsStore, Clock clock) {

        this.settingsRepository = settingsRepository;
        this.settingsStore = settingsStore;
        this.clock = clock;
    }

    public AdvisorSettings resolve(ProfileInfo profile) {
        return resolve(profile.id());
    }

    /**
     * The settings for a profile: the source folder it has stored (blank until configured) plus the
     * installation-wide prune threshold. Works for every profile, including a locally-uploaded Quick
     * Analysis recording that has no project.
     */
    public AdvisorSettings resolve(String profileId) {
        return new AdvisorSettings(sourcePath(profileId), pruneThresholdPct());
    }

    public AdvisorSettings save(String profileId, AdvisorSettings settings) {
        settingsRepository.upsert(new AdvisorSettingsRow(
                profileId,
                settings.sourcePath(),
                clock.instant()));

        LOG.info("Saved advisor settings: profile_id={} has_source_path={}",
                profileId, settings.hasSourcePath());
        return settings;
    }

    private String sourcePath(String profileId) {
        return settingsRepository.find(profileId)
                .map(AdvisorSettingsRow::sourcePath)
                .orElse("");
    }

    private double pruneThresholdPct() {
        double stored = settingsStore.getDouble(
                MicroscopeSettingKeys.ADVISOR_PRUNE_THRESHOLD_PCT, AdvisorSettings.DEFAULT_PRUNE_THRESHOLD_PCT);
        if (stored <= 0 || stored >= 100) {
            LOG.warn("Ignoring out-of-range global prune threshold: prune_threshold_pct={}", stored);
            return AdvisorSettings.DEFAULT_PRUNE_THRESHOLD_PCT;
        }
        return stored;
    }
}
