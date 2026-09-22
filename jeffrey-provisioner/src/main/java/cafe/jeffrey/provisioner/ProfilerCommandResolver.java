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


package cafe.jeffrey.provisioner;

import cafe.jeffrey.provisioner.placeholder.Placeholders;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.config.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the configured async-profiler settings into the command a JVM is actually started with.
 *
 * <p>Choosing between sources is not this class's job any more: the configuration merge already
 * settled that, by the ordinary layer precedence, and reported which layer won. What is left is
 * the part that could not happen earlier — the placeholders that need the session directory, and
 * the feature flags the provisioner appends — plus the built-in default when nothing was set.</p>
 */
public class ProfilerCommandResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerCommandResolver.class);

    private static final String FEATURE_SEPARATOR = " ";

    /**
     * The resolved command together with the layer its base came from.
     *
     * @param command the full JVM options, placeholders expanded and features appended
     * @param source  which configuration layer supplied the command, or that none did
     */
    public record ResolvedProfilerCommand(String command, ConfigSource source) {
    }

    /**
     * @param asprofSettings the configured command, or null when no layer set one
     * @param source         the layer that set it, as the merge reported it
     * @param placeholders   resolves {@code <<JEFFREY:...>>}, which needs the session layout
     * @param features       the flags the provisioner adds for the enabled features
     */
    public ResolvedProfilerCommand resolve(
            String asprofSettings, ConfigSource source, Placeholders placeholders, String features) {

        String configured = asprofSettings;
        ConfigSource resolvedSource = source;
        if (configured == null || configured.isBlank()) {
            configured = CliConstants.DEFAULT_ASPROF_SETTINGS;
            resolvedSource = ConfigSource.BUILT_IN;
        }

        LOG.info("Profiler command resolved: source={}", resolvedSource);

        // Hub-published settings never pass through the configuration phase's placeholder pass, so
        // this is the only place their <<JEFFREY:...>> placeholders become answerable.
        String command = placeholders.resolve(configured) + FEATURE_SEPARATOR + features;
        return new ResolvedProfilerCommand(command, resolvedSource);
    }
}
