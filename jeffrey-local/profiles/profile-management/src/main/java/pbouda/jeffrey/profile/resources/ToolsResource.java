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
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.manager.ProfileToolsManager;
import pbouda.jeffrey.profile.manager.ProfileToolsManager.RenamePreviewResult;
import pbouda.jeffrey.profile.manager.ProfileToolsManager.RenameRequest;
import pbouda.jeffrey.profile.manager.ProfileToolsManager.RenameResult;
import pbouda.jeffrey.profile.tools.collapse.CollapseFramesManager;
import pbouda.jeffrey.profile.tools.collapse.CollapseFramesManager.CollapseApplyResult;
import pbouda.jeffrey.profile.tools.collapse.CollapseFramesManager.CollapsePreviewResult;
import pbouda.jeffrey.profile.tools.collapse.CollapseFramesManager.CollapseRequest;

public class ToolsResource {

    private static final Logger LOG = LoggerFactory.getLogger(ToolsResource.class);

    private final ProfileManager profileManager;
    private final ProfileToolsManager toolsManager;
    private final CollapseFramesManager collapseManager;

    public ToolsResource(
            ProfileManager profileManager,
            ProfileToolsManager toolsManager,
            CollapseFramesManager collapseManager) {

        this.profileManager = profileManager;
        this.toolsManager = toolsManager;
        this.collapseManager = collapseManager;
    }

    // --- Rename Frames (existing) ---

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
        RenameResult result = toolsManager.executeRename(request);
        profileManager.markModified();
        return result;
    }

    // --- Collapse Frames ---

    @POST
    @Path("/collapse-frames/preview")
    public CollapsePreviewResult previewCollapse(CollapseRequest request) {
        LOG.debug("Previewing frame collapse: patterns={} label={}", request.patterns(), request.label());
        return collapseManager.preview(request);
    }

    @POST
    @Path("/collapse-frames")
    public CollapseApplyResult executeCollapse(CollapseRequest request) {
        LOG.info("Executing frame collapse: patterns={} label={}", request.patterns(), request.label());
        CollapseApplyResult result = collapseManager.execute(request);
        profileManager.markModified();
        return result;
    }

}
