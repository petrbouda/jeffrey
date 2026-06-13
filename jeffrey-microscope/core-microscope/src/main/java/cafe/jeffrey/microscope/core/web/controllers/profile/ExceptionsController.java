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
