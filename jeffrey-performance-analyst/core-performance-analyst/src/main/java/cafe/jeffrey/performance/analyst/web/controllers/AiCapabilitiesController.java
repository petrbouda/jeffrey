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

package cafe.jeffrey.performance.analyst.web.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.performance.analyst.ai.AiCapabilitiesResponse;

/**
 * Always-on endpoint reporting which AI features the deployment can serve. Unlike the AI-gated feature
 * controllers (e.g. recommendations), this one is registered regardless of the configured provider so the
 * frontend can discover whether AI is available and gate AI actions accordingly.
 */
@RestController
@RequestMapping("/api/internal/ai")
public class AiCapabilitiesController {

    private static final String PROVIDER_NONE = "none";

    private final boolean recommendationsEnabled;

    public AiCapabilitiesController(
            @Value("${jeffrey.performance-analyst.ai.provider:none}") String aiProvider) {
        this.recommendationsEnabled = !PROVIDER_NONE.equals(aiProvider);
    }

    @GetMapping("/capabilities")
    public AiCapabilitiesResponse capabilities() {
        return new AiCapabilitiesResponse(recommendationsEnabled);
    }
}
