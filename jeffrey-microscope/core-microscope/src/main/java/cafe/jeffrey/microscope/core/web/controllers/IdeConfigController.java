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

package cafe.jeffrey.microscope.core.web.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/config/ide")
public class IdeConfigController {

    private final boolean enabled;
    private final String baseUrl;

    public IdeConfigController(
            @Value("${jeffrey.microscope.ide.enabled:false}") boolean enabled,
            @Value("${jeffrey.microscope.ide.base-url:}") String baseUrl) {
        this.enabled = enabled;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? null : baseUrl;
    }

    @GetMapping
    public IdeConfigResponse get() {
        return new IdeConfigResponse(enabled, baseUrl);
    }

    public record IdeConfigResponse(boolean enabled, String baseUrl) {
    }
}
