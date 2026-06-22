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

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.performance.analyst.recommendations.TopRecommendationsManager;
import cafe.jeffrey.performance.analyst.recommendations.TopSeverityOverviewResponse;

/**
 * Serves the global Overview's "Highest Impact" list: the most severe recommendations across all projects.
 * Always registered (unlike the AI-gated recommendation controller) because it only reads already-stored
 * rows, so the Overview works even when no AI provider is currently configured.
 */
@RestController
@RequestMapping("/api/internal/recommendations")
public class TopRecommendationsController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final TopRecommendationsManager topRecommendationsManager;

    public TopRecommendationsController(TopRecommendationsManager topRecommendationsManager) {
        this.topRecommendationsManager = topRecommendationsManager;
    }

    @GetMapping(value = "/top-severity", produces = MediaType.APPLICATION_JSON_VALUE)
    public TopSeverityOverviewResponse topSeverity(@RequestParam(value = "limit", required = false) Integer limit) {
        int effectiveLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return topRecommendationsManager.overview(effectiveLimit);
    }
}
