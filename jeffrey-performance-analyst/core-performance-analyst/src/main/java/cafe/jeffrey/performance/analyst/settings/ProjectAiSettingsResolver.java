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

package cafe.jeffrey.performance.analyst.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.performance.analyst.persistence.ProjectAiConfiguration;
import cafe.jeffrey.performance.analyst.persistence.ProjectAiConfigurationRepository;

import java.time.Clock;
import java.util.Optional;

/**
 * Reads a project's analysis settings, falling back to the installation defaults.
 *
 * <p>The {@code project_ai_configuration} table has existed since the first migration but nothing ever
 * read it — the exporter hardcoded the threshold the table was meant to supply. This resolver is the
 * consumer it was missing, so the stored value now reaches the code that uses it.</p>
 */
public class ProjectAiSettingsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectAiSettingsResolver.class);

    private final ProjectAiConfigurationRepository configurationRepository;
    private final Clock clock;

    public ProjectAiSettingsResolver(ProjectAiConfigurationRepository configurationRepository, Clock clock) {
        this.configurationRepository = configurationRepository;
        this.clock = clock;
    }

    /**
     * The settings for {@code projectId}, or the defaults when the project is unknown, unscoped, or has
     * never been configured.
     */
    public ProjectAiSettings resolve(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return ProjectAiSettings.defaults();
        }
        return configurationRepository.find(projectId)
                .map(ProjectAiSettingsResolver::toSettings)
                .orElseGet(ProjectAiSettings::defaults);
    }

    /**
     * Stores the prune threshold for a project, preserving whatever provider/model the row already
     * carried so a settings screen that only edits the threshold cannot silently clear the rest.
     */
    public ProjectAiSettings save(String projectId, double pruneThresholdPct, double regressionThresholdPp) {
        ProjectAiSettings settings = new ProjectAiSettings(pruneThresholdPct, regressionThresholdPp);

        Optional<ProjectAiConfiguration> existing = configurationRepository.find(projectId);
        configurationRepository.upsert(new ProjectAiConfiguration(
                projectId,
                existing.map(ProjectAiConfiguration::provider).orElse(null),
                existing.map(ProjectAiConfiguration::model).orElse(null),
                settings.pruneThresholdPct(),
                settings.regressionThresholdPp(),
                clock.instant()));

        LOG.info("Saved project AI settings: project_id={} prune_threshold_pct={} regression_threshold_pp={}",
                projectId, settings.pruneThresholdPct(), settings.regressionThresholdPp());
        return settings;
    }

    private static ProjectAiSettings toSettings(ProjectAiConfiguration configuration) {
        ProjectAiSettings defaults = ProjectAiSettings.defaults();
        double pruneThresholdPct = configuration.pruneThresholdPct();
        double regressionThresholdPp = configuration.regressionThresholdPp();

        // A row written before validation existed, or edited by hand, must not break analysis; fall
        // back per field rather than discarding a good value because its neighbour is bad.
        if (pruneThresholdPct <= 0 || pruneThresholdPct >= 100) {
            LOG.warn("Ignoring out-of-range stored prune threshold: project_id={} prune_threshold_pct={}",
                    configuration.projectId(), pruneThresholdPct);
            pruneThresholdPct = defaults.pruneThresholdPct();
        }
        if (regressionThresholdPp < 0) {
            LOG.warn("Ignoring negative stored regression threshold: project_id={} regression_threshold_pp={}",
                    configuration.projectId(), regressionThresholdPp);
            regressionThresholdPp = defaults.regressionThresholdPp();
        }
        return new ProjectAiSettings(pruneThresholdPct, regressionThresholdPp);
    }
}
