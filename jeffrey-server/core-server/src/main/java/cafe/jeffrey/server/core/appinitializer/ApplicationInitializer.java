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

package cafe.jeffrey.server.core.appinitializer;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.model.ProfilerInfo;
import cafe.jeffrey.server.persistence.api.ProfilerRepository;

public class ApplicationInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final ProfilerRepository profilerRepository;

    public ApplicationInitializer(ProfilerRepository profilerRepository) {
        this.profilerRepository = profilerRepository;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();
        initializeProfilerSettings(environment);
    }

    private void initializeProfilerSettings(ConfigurableEnvironment environment) {
        boolean createGlobalSettings = environment.getProperty(
                "jeffrey.server.profiler.global-settings.create-if-not-exists", Boolean.class, true);
        String globalCommand = environment.getProperty(
                "jeffrey.server.profiler.global-settings.command", String.class, CliConstants.DEFAULT_PROFILER_CONFIG);

        if (createGlobalSettings && !globalCommand.isBlank()) {
            profilerRepository.upsertSettings(new ProfilerInfo(null, null, globalCommand));
        }
    }
}
