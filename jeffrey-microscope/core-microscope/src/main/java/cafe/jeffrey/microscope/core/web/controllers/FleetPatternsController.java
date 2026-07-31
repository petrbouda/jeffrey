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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.profile.advisor.fleet.FleetPatterns;
import cafe.jeffrey.profile.advisor.fleet.FleetPatternsService;

/**
 * Hotspots that recur across projects. Not profile-scoped, because the whole point is the question no
 * single profile can answer: when the same frame costs several services time, the fix usually belongs
 * where that frame lives rather than in each caller.
 */
@RestController
@RequestMapping("/api/internal/advisor/fleet-patterns")
public class FleetPatternsController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final FleetPatternsService fleetPatternsService;

    public FleetPatternsController(FleetPatternsService fleetPatternsService) {
        this.fleetPatternsService = fleetPatternsService;
    }

    @GetMapping
    public FleetPatterns patterns(
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {

        return fleetPatternsService.patterns(Math.clamp(limit, 1, MAX_LIMIT));
    }
}
