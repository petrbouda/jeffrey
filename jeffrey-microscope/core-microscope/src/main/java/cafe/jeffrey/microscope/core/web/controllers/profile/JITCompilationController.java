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
import cafe.jeffrey.profile.common.event.JITCompilationStats;
import cafe.jeffrey.profile.common.event.JITLongCompilation;
import cafe.jeffrey.profile.manager.JITCompilationManager;
import cafe.jeffrey.profile.manager.model.jit.CodeCacheData;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/compilation")
public class JITCompilationController {

    private static final Logger LOG = LoggerFactory.getLogger(JITCompilationController.class);
    private static final int MAX_COMPILATIONS = 20;

    private final ProfileManagerResolver resolver;

    public JITCompilationController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/statistics")
    public JITCompilationStats statistics(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching JIT compilation statistics");
        return mgr(profileId).statistics();
    }

    @GetMapping("/compilations")
    public List<JITLongCompilation> compilations(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching JIT compilations");
        return mgr(profileId).compilations(MAX_COMPILATIONS);
    }

    @GetMapping("/timeseries")
    public SingleSerie timeseries(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching JIT compilation timeseries");
        return mgr(profileId).timeseries();
    }

    @GetMapping("/queue-timeline")
    public TimeseriesData queueTimeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching compiler queue timeline");
        return mgr(profileId).compilerQueueTimeline();
    }

    @GetMapping("/code-cache")
    public CodeCacheData codeCache(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching code cache statistics");
        return mgr(profileId).codeCache();
    }

    private JITCompilationManager mgr(String profileId) {
        return resolver.resolve(profileId).jitCompilationManager();
    }
}
