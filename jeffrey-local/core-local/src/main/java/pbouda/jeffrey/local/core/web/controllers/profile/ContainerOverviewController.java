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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.manager.model.container.ContainerConfigurationData;

@RestController
@RequestMapping({
        "/api/internal/profiles/{profileId}/container",
        "/api/internal/quick-analysis/profiles/{profileId}/container",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{profileId}/container"
})
public class ContainerOverviewController {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerOverviewController.class);

    private final ProfileManagerResolver resolver;

    public ContainerOverviewController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/configuration")
    public ContainerConfigurationData configuration(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching container configuration");
        return resolver.resolve(profileId).containerManager().configuration();
    }
}
