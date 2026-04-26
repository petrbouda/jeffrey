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

package pbouda.jeffrey.local.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.manager.ProfileToolsManager.RenamePreviewResult;
import pbouda.jeffrey.profile.manager.ProfileToolsManager.RenameRequest;
import pbouda.jeffrey.profile.manager.ProfileToolsManager.RenameResult;
import pbouda.jeffrey.profile.tools.collapse.CollapseFramesManager.CollapseApplyResult;
import pbouda.jeffrey.profile.tools.collapse.CollapseFramesManager.CollapsePreviewResult;
import pbouda.jeffrey.profile.tools.collapse.CollapseFramesManager.CollapseRequest;

@RestController
@RequestMapping({
        "/api/internal/profiles/{profileId}/tools",
        "/api/internal/quick-analysis/profiles/{profileId}/tools",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{profileId}/tools"
})
public class ToolsController {

    private static final Logger LOG = LoggerFactory.getLogger(ToolsController.class);

    private final ProfileManagerResolver resolver;

    public ToolsController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping("/rename-frames/preview")
    public RenamePreviewResult previewRename(
            @PathVariable("profileId") String profileId,
            @RequestBody RenameRequest request) {
        LOG.debug("Previewing frame rename: search={} replacement={}", request.search(), request.replacement());
        return resolver.resolve(profileId).toolsManager().previewRename(request);
    }

    @PostMapping("/rename-frames")
    public RenameResult executeRename(
            @PathVariable("profileId") String profileId,
            @RequestBody RenameRequest request) {
        LOG.info("Executing frame rename: search={} replacement={}", request.search(), request.replacement());
        ProfileManager pm = resolver.resolve(profileId);
        RenameResult result = pm.toolsManager().executeRename(request);
        pm.markModified();
        return result;
    }

    @PostMapping("/collapse-frames/preview")
    public CollapsePreviewResult previewCollapse(
            @PathVariable("profileId") String profileId,
            @RequestBody CollapseRequest request) {
        LOG.debug("Previewing frame collapse: patterns={} label={}", request.patterns(), request.label());
        return resolver.resolve(profileId).collapseFramesManager().preview(request);
    }

    @PostMapping("/collapse-frames")
    public CollapseApplyResult executeCollapse(
            @PathVariable("profileId") String profileId,
            @RequestBody CollapseRequest request) {
        LOG.info("Executing frame collapse: patterns={} label={}", request.patterns(), request.label());
        ProfileManager pm = resolver.resolve(profileId);
        CollapseApplyResult result = pm.collapseFramesManager().execute(request);
        pm.markModified();
        return result;
    }
}
