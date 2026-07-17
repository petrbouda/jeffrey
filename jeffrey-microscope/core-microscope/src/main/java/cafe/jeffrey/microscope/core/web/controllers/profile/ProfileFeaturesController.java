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
import cafe.jeffrey.profile.ai.duckdb.jfr.service.JfrAnalysisAssistantService;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.RecordingFormatRegistry;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/features")
public class ProfileFeaturesController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileFeaturesController.class);

    private final ProfileManagerResolver resolver;
    private final JfrAnalysisAssistantService assistantService;
    private final RecordingFormatRegistry recordingFormats;

    public ProfileFeaturesController(
            ProfileManagerResolver resolver,
            JfrAnalysisAssistantService assistantService,
            RecordingFormatRegistry recordingFormats) {
        this.resolver = resolver;
        this.assistantService = assistantService;
        this.recordingFormats = recordingFormats;
    }

    @GetMapping("/disabled")
    public List<FeatureType> disabledFeatures(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching disabled features");
        ProfileManager pm = resolver.resolve(profileId);
        HeapDumpManager heapDumpManager = pm.heapDumpManager();
        List<FeatureType> disabled = new ArrayList<>(pm.featuresManager().getDisabledFeatures());
        if (!assistantService.isAvailable()) {
            disabled.add(FeatureType.AI_ANALYSIS);
        }
        if (!heapDumpManager.heapDumpExists() || !heapDumpManager.isCacheReady()) {
            disabled.add(FeatureType.HEAP_DUMP);
        }
        // Aggregated formats (e.g. pprof) carry no per-sample timestamps, so the time-resolved
        // views (subsecond section + the timeseries strip above the flamegraph) collapse into a
        // single spike and convey no information.
        var format = recordingFormats.bySource(pm.info().eventSource());
        if (!format.capabilities().timestampedEvents()) {
            disabled.add(FeatureType.SUBSECOND);
            disabled.add(FeatureType.TIMESERIES);
        }
        return disabled;
    }
}
