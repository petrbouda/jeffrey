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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.shared.common.JeffreyVersion;

import java.util.Map;

/**
 * Shared {@code GET /api/internal/version} endpoint, identical across all deployments. Registered via
 * {@link VersionFeatureConfiguration} (it lives outside the deployments' component-scan roots).
 * Deployment-specific extensions (e.g. microscope's update check) live in supplementary controllers.
 */
@RestController
@RequestMapping("/api/internal")
public class VersionController {

    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of("version", JeffreyVersion.resolveJeffreyVersion());
    }
}
