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
import cafe.jeffrey.profile.manager.memory.NativeMemoryManager;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryActivityData;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryInfo;
import cafe.jeffrey.profile.manager.model.nativememory.NativeMemoryOverview;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/native-memory")
public class NativeMemoryController {

    private static final Logger LOG = LoggerFactory.getLogger(NativeMemoryController.class);

    private final ProfileManagerResolver resolver;

    public NativeMemoryController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public NativeMemoryOverview overview(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching native-memory overview");
        return mgr(profileId).overview();
    }

    @GetMapping("/timeline")
    public TimeseriesData rssTimeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching RSS timeline");
        return mgr(profileId).rssTimeline();
    }

    @GetMapping("/direct-buffers/timeline")
    public TimeseriesData directBufferTimeline(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching direct-buffer timeline");
        return mgr(profileId).directBufferTimeline();
    }

    @GetMapping("/native-libraries")
    public List<NativeLibraryInfo> nativeLibraries(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching native libraries");
        return mgr(profileId).nativeLibraries();
    }

    @GetMapping("/library-activity")
    public NativeLibraryActivityData nativeLibraryActivity(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching native-library load/unload activity");
        return mgr(profileId).nativeLibraryActivity();
    }

    private NativeMemoryManager mgr(String profileId) {
        return resolver.resolve(profileId).nativeMemoryManager();
    }
}
