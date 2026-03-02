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

package pbouda.jeffrey.profile.resources;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.manager.ProfileToolsManager;
import pbouda.jeffrey.profile.manager.ProfileToolsManager.RenamePreviewResult;
import pbouda.jeffrey.profile.manager.ProfileToolsManager.RenameRequest;
import pbouda.jeffrey.profile.manager.ProfileToolsManager.RenameResult;

public class ToolsResource {

    private static final Logger LOG = LoggerFactory.getLogger(ToolsResource.class);

    private final ProfileToolsManager toolsManager;

    public ToolsResource(ProfileToolsManager toolsManager) {
        this.toolsManager = toolsManager;
    }

    @POST
    @Path("/rename-frames/preview")
    public RenamePreviewResult previewRename(RenameRequest request) {
        LOG.debug("Previewing frame rename: search={} replacement={}", request.search(), request.replacement());
        return toolsManager.previewRename(request);
    }

    @POST
    @Path("/rename-frames")
    public RenameResult executeRename(RenameRequest request) {
        LOG.info("Executing frame rename: search={} replacement={}", request.search(), request.replacement());
        return toolsManager.executeRename(request);
    }
}
