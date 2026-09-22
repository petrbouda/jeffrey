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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.model.repository.ProfilerSettingsSource;

/**
 * Picks the async-profiler command a session runs with. Two sources only: the command the
 * provisioner was configured with ({@code profiler-command} in the HOCON file or
 * {@code JEFFREY_PROFILER_COMMAND} in the environment, already merged by then), and the built-in
 * default it falls back to.
 */
public class ProfilerSettingsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerSettingsResolver.class);

    /** The resolved profiler command together with the source that won. */
    public record ResolvedProfilerSettings(String command, ProfilerSettingsSource source) {
    }

    public ResolvedProfilerSettings resolve(String profilerCommand, Placeholders placeholders, String features) {
        ResolvedProfilerSettings resolved = configured(profilerCommand);

        LOG.info("Profiler command resolved: source={}", resolved.source());

        String command = placeholders.resolve(resolved.command()) + " " + features;
        return new ResolvedProfilerSettings(command, resolved.source());
    }

    private static ResolvedProfilerSettings configured(String profilerCommand) {
        if (profilerCommand != null && !profilerCommand.isBlank()) {
            return new ResolvedProfilerSettings(profilerCommand, ProfilerSettingsSource.CONFIGURED);
        }
        return new ResolvedProfilerSettings(CliConstants.DEFAULT_PROFILER_CONFIG, ProfilerSettingsSource.BUILT_IN);
    }
}
