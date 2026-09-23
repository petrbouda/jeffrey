/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
