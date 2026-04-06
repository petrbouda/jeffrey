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

package pbouda.jeffrey.local.core.resources.project;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.local.core.resources.request.ProjectSettingsUpdateRequest;
import pbouda.jeffrey.local.core.resources.response.ProjectSettingsResponse;
import pbouda.jeffrey.shared.common.model.ProjectInfo;

import java.util.function.Consumer;

public class ProjectSettingsResource {

    private final ProjectInfo projectInfo;
    private final Consumer<String> nameUpdater;

    public ProjectSettingsResource(
            ProjectInfo projectInfo,
            Consumer<String> nameUpdater) {
        this.projectInfo = projectInfo;
        this.nameUpdater = nameUpdater;
    }

    @GET
    public ProjectSettingsResponse settings() {
        return ProjectSettingsResponse.create(projectInfo);
    }

    @POST
    public void update(ProjectSettingsUpdateRequest request) {
        if (request.name() != null) {
            nameUpdater.accept(request.name());
        }
    }
}
