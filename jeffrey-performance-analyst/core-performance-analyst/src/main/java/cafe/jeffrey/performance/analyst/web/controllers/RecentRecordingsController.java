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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.performance.analyst.recordings.RecentRecordingsManager;
import cafe.jeffrey.shared.ui.workspace.dto.RecordingResponse;

import java.util.List;

/**
 * Lists the most-recent recordings across all projects for the analyst's global recordings view.
 * Since every downloaded recording is project-scoped, the global view spans projects rather than the
 * (now empty) unscoped set.
 */
@RestController
@RequestMapping("/api/internal/recordings/recent")
public class RecentRecordingsController {

    private static final int DEFAULT_RECENT_LIMIT = 20;

    private final RecentRecordingsManager recentRecordingsManager;

    public RecentRecordingsController(RecentRecordingsManager recentRecordingsManager) {
        this.recentRecordingsManager = recentRecordingsManager;
    }

    @GetMapping
    public List<RecordingResponse> list(@RequestParam(value = "limit", required = false) Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_RECENT_LIMIT;
        return recentRecordingsManager.latest(effectiveLimit);
    }
}
