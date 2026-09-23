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
import cafe.jeffrey.profile.manager.ExceptionsManager;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionTypeStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionsOverview;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/exceptions")
public class ExceptionsController {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionsController.class);

    private final ProfileManagerResolver resolver;

    public ExceptionsController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public ExceptionsOverview overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching exceptions overview");
        return mgr(profileId).overview();
    }

    @GetMapping("/timeline")
    public TimeseriesData timeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching exceptions timeline");
        return mgr(profileId).timeline();
    }

    @GetMapping("/top-types")
    public List<ExceptionTypeStat> topTypes(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching top exception types");
        return mgr(profileId).topTypes();
    }

    private ExceptionsManager mgr(String profileId) {
        return resolver.resolve(profileId).exceptionsManager();
    }
}
