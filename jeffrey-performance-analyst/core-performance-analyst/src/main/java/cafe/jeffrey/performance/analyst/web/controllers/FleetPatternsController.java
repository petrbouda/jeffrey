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
import cafe.jeffrey.performance.analyst.fleet.FleetPatternsManager;
import cafe.jeffrey.performance.analyst.fleet.FleetPatternsResponse;

/**
 * Serves the Overview's fleet-wide pattern rollup: the hotspots that recur across projects. Read-only
 * over already-stored claims, so it is available whether or not an AI provider is currently configured.
 */
@RestController
@RequestMapping("/api/internal/fleet-patterns")
public class FleetPatternsController {

    private static final int DEFAULT_LIMIT = 10;

    private final FleetPatternsManager fleetPatternsManager;

    public FleetPatternsController(FleetPatternsManager fleetPatternsManager) {
        this.fleetPatternsManager = fleetPatternsManager;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public FleetPatternsResponse patterns(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return new FleetPatternsResponse(
                fleetPatternsManager.patterns(limit), fleetPatternsManager.analyzedProjectCount());
    }
}
