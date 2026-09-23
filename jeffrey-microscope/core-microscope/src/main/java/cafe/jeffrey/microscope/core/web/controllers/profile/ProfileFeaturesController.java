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
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.model.RecordingEventSource;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/features")
public class ProfileFeaturesController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileFeaturesController.class);

    private final ProfileManagerResolver resolver;

    public ProfileFeaturesController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/disabled")
    public List<FeatureType> disabledFeatures(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching disabled features");
        ProfileManager pm = resolver.resolve(profileId);
        HeapDumpManager heapDumpManager = pm.heapDumpManager();
        List<FeatureType> disabled = new ArrayList<>(pm.featuresManager().getDisabledFeatures());
        if (!heapDumpManager.heapDumpExists() || !heapDumpManager.isCacheReady()) {
            disabled.add(FeatureType.HEAP_DUMP);
        }
        // pprof profiles are aggregated and carry no per-sample timestamps, so the time-resolved
        // views (subsecond section + the timeseries strip above the flamegraph) collapse into a
        // single spike and convey no information.
        if (pm.info().eventSource() == RecordingEventSource.PPROF) {
            disabled.add(FeatureType.SUBSECOND);
            disabled.add(FeatureType.TIMESERIES);
        }
        return disabled;
    }
}
