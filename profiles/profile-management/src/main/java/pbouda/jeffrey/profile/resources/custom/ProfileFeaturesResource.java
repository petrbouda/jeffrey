/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.resources.custom;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantService;
import pbouda.jeffrey.profile.feature.FeatureType;
import pbouda.jeffrey.profile.manager.HeapDumpManager;
import pbouda.jeffrey.profile.manager.ProfileFeaturesManager;

import java.util.ArrayList;
import java.util.List;

public class ProfileFeaturesResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileFeaturesResource.class);

    private final ProfileFeaturesManager featuresManager;
    private final JfrAnalysisAssistantService assistantService;
    private final HeapDumpManager heapDumpManager;

    public ProfileFeaturesResource(
            ProfileFeaturesManager featuresManager,
            JfrAnalysisAssistantService assistantService,
            HeapDumpManager heapDumpManager) {
        this.featuresManager = featuresManager;
        this.assistantService = assistantService;
        this.heapDumpManager = heapDumpManager;
    }

    @GET
    @Path("disabled")
    public List<FeatureType> disabledFeatures() {
        LOG.debug("Fetching disabled features");
        List<FeatureType> disabled = new ArrayList<>(featuresManager.getDisabledFeatures());
        if (!assistantService.isAvailable()) {
            disabled.add(FeatureType.AI_ANALYSIS);
        }
        if (!heapDumpManager.heapDumpExists() || !heapDumpManager.isCacheReady()) {
            disabled.add(FeatureType.HEAP_DUMP);
        }
        return disabled;
    }
}
