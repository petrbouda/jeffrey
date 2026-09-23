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
