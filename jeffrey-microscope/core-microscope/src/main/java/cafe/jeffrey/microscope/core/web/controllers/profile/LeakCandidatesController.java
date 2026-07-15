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
import cafe.jeffrey.profile.manager.memory.LeakCandidatesManager;
import cafe.jeffrey.profile.manager.model.leak.LeakCandidate;
import cafe.jeffrey.profile.manager.model.leak.LeakOverview;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/leak-candidates")
public class LeakCandidatesController {

    private static final Logger LOG = LoggerFactory.getLogger(LeakCandidatesController.class);

    private final ProfileManagerResolver resolver;

    public LeakCandidatesController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public LeakOverview overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching leak-candidates overview");
        return mgr(profileId).overview();
    }

    @GetMapping("/candidates")
    public List<LeakCandidate> candidates(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching leak candidates");
        return mgr(profileId).candidates();
    }

    private LeakCandidatesManager mgr(String profileId) {
        return resolver.resolve(profileId).leakCandidatesManager();
    }
}
