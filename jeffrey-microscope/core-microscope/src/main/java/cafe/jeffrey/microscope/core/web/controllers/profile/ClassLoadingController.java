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
