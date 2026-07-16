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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.thread.VirtualThreadManager;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/virtual-threads")
public class VirtualThreadController {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualThreadController.class);

    private final ProfileManagerResolver resolver;

    public VirtualThreadController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public VirtualThreadData virtualThreadData(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching virtual-thread dashboard data");
        return mgr(profileId).virtualThreadData();
    }

    private VirtualThreadManager mgr(String profileId) {
        return resolver.resolve(profileId).virtualThreadManager();
    }
}
