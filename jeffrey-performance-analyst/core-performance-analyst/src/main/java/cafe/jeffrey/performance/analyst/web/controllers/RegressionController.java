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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.performance.analyst.regression.ProfileComparison;
import cafe.jeffrey.performance.analyst.regression.RegressionManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

/**
 * Compares a recording's profile against an earlier baseline recording of the same event type. Reads
 * only cached prompt indexes, so a comparison never re-parses a JFR file and never calls a model.
 */
@RestController
@RequestMapping("/api/internal/projects/{projectId}/recordings/{recordingId}/regression")
public class RegressionController {

    private final RegressionManager regressionManager;

    public RegressionController(RegressionManager regressionManager) {
        this.regressionManager = regressionManager;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ProfileComparison compare(
            @PathVariable("projectId") String projectId,
            @PathVariable("recordingId") String recordingId,
            @RequestParam("baselineRecordingId") String baselineRecordingId,
            @RequestParam("eventType") String eventType) {

        if (baselineRecordingId.equals(recordingId)) {
            throw Exceptions.invalidRequest("A recording cannot be compared against itself.");
        }
        return regressionManager.compare(projectId, baselineRecordingId, recordingId, eventType);
    }
}
