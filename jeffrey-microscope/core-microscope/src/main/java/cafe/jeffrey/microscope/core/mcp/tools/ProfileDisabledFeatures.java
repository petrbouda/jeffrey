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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The analysis features one profile has no data for, as the MCP tools report them.
 * <p>
 * The same reasoning {@code ProfileFeaturesController} applies, minus its AI-analysis check: that one
 * describes whether Jeffrey's own assistant is configured, which says nothing about what this profile
 * holds — and the client asking is an assistant already. {@code profiles_features},
 * {@code profiles_summary} and {@code profiles_evidence} each report it, and they have to agree.
 */
final class ProfileDisabledFeatures {

    private ProfileDisabledFeatures() {
    }

    static List<FeatureType> of(ProfileManager profileManager) {
        List<FeatureType> disabled = new ArrayList<>(profileManager.featuresManager().getDisabledFeatures());
        HeapDumpManager heapDumpManager = profileManager.heapDumpManager();
        if (!heapDumpManager.heapDumpExists() || !heapDumpManager.isCacheReady()) {
            disabled.add(FeatureType.HEAP_DUMP);
        }
        // pprof profiles are aggregated and carry no per-sample timestamps, so the time-resolved views
        // collapse into a single spike and convey no information.
        if (profileManager.info().eventSource() == RecordingEventSource.PPROF) {
            disabled.add(FeatureType.SUBSECOND);
            disabled.add(FeatureType.TIMESERIES);
        }
        return disabled;
    }
}
