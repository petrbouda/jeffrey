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

package cafe.jeffrey.shared.ui.version;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shared {@link VersionController} as a {@code @Bean}. The controller is
 * {@code @RestController}-annotated but lives in {@code cafe.jeffrey.shared.ui.version}, outside the
 * deployments' component-scan roots, so it is registered exactly once via this explicit {@code @Bean}.
 * Each deployment {@code @Import}s this configuration.
 */
@Configuration
public class VersionFeatureConfiguration {

    @Bean
    public VersionController versionFeatureVersionController() {
        return new VersionController();
    }
}
