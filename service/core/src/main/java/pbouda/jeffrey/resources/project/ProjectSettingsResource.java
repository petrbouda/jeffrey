/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.resources.project;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.manager.SettingsManager;
import pbouda.jeffrey.resources.util.Formatter;

import java.time.Instant;

public class ProjectSettingsResource {

    public record SettingsResponse(
            String id,
            String name,
            String description,
            String createdAt) {

        public SettingsResponse(ProjectInfo projectInfo) {
            this(projectInfo.id(), projectInfo.name(), null, Formatter.formatInstant(projectInfo.createdAt()));
        }
    }

    public record ProjectSettingsUpdate(String name) {
    }

    private final SettingsManager settingsManager;

    public ProjectSettingsResource(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @POST
    public void update(ProjectSettingsUpdate settings) {
        if (settings.name != null) {
            settingsManager.updateName(settings.name);
        }
    }

    @GET
    public SettingsResponse settings() {
        return settingsManager.info()
                .map(SettingsResponse::new)
                .orElseThrow(NotFoundException::new);
    }
}
