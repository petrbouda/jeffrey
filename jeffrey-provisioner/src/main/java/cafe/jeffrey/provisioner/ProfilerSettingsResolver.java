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

package cafe.jeffrey.provisioner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import cafe.jeffrey.shared.common.CliConstants;

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
