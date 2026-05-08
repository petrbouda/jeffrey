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

package cafe.jeffrey.server.core.web.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.server.core.scheduler.JobRegistry;
import cafe.jeffrey.shared.common.model.job.JobInfo;

import java.util.List;

/**
 * Read-only view of all scheduler jobs configured on this server. The list is
 * resolved from {@code application.properties} at startup; to change a job's
 * settings edit the properties file and restart the server.
 */
@RestController
@RequestMapping("/api/internal/scheduler")
public class SchedulerController {

    private final JobRegistry jobRegistry;

    public SchedulerController(JobRegistry jobRegistry) {
        this.jobRegistry = jobRegistry;
    }

    @GetMapping("/jobs")
    public List<JobInfo> jobs() {
        return jobRegistry.all();
    }
}
