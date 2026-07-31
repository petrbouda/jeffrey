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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.performance.analyst.settings.ProjectAiSettings;
import cafe.jeffrey.performance.analyst.settings.ProjectAiSettingsRequest;
import cafe.jeffrey.performance.analyst.settings.ProjectAiSettingsResolver;
import cafe.jeffrey.shared.common.exception.Exceptions;

/**
 * Reads and writes a project's analysis settings — the prune threshold that decides how much of the
 * call tree reaches the model, and the percentage-point move a frame must make to count as regressed.
 *
 * <p>These live in {@code project_ai_configuration}, a table that existed from the first migration but
 * had no reader until now. This is the endpoint that makes the stored value mean something.</p>
 */
@RestController
@RequestMapping("/api/internal/projects/{projectId}/ai-settings")
public class ProjectAiSettingsController {

    private final ProjectAiSettingsResolver settingsResolver;

    public ProjectAiSettingsController(ProjectAiSettingsResolver settingsResolver) {
        this.settingsResolver = settingsResolver;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ProjectAiSettings get(@PathVariable("projectId") String projectId) {
        return settingsResolver.resolve(projectId);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ProjectAiSettings save(
            @PathVariable("projectId") String projectId,
            @RequestBody ProjectAiSettingsRequest request) {

        try {
            return settingsResolver.save(projectId, request.pruneThresholdPct());
        } catch (IllegalArgumentException e) {
            // The record validates its own invariants; map that to a client error at the boundary
            // rather than letting a domain exception surface as a 500.
            throw Exceptions.invalidRequest(e.getMessage());
        }
    }
}
