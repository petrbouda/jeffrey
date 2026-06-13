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
import cafe.jeffrey.profile.manager.ClassLoadingManager;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadActivity;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoaderStat;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadingOverview;
import cafe.jeffrey.profile.manager.model.classloading.RedefinitionData;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/class-loading")
public class ClassLoadingController {

    private static final Logger LOG = LoggerFactory.getLogger(ClassLoadingController.class);

    private final ProfileManagerResolver resolver;

    public ClassLoadingController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public ClassLoadingOverview overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching class-loading overview");
        return mgr(profileId).overview();
    }

    @GetMapping("/timeline")
    public TimeseriesData timeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching class-loading timeline");
        return mgr(profileId).timeline();
    }

    @GetMapping("/class-loaders")
    public List<ClassLoaderStat> classLoaders(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching class loaders");
        return mgr(profileId).classLoaders();
    }

    @GetMapping("/class-loads")
    public ClassLoadActivity classLoads(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching class-load activity");
        return mgr(profileId).classLoadActivity();
    }

    @GetMapping("/redefinitions")
    public RedefinitionData redefinitions(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching class redefinitions");
        return mgr(profileId).redefinitions();
    }

    private ClassLoadingManager mgr(String profileId) {
        return resolver.resolve(profileId).classLoadingManager();
    }
}
