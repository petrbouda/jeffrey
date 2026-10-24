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

package cafe.jeffrey.profile.feature;

import cafe.jeffrey.microscope.model.ProfileKind;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The analysis features one profile has no data for. The Profile Detail, the MCP tools and the IDE
 * panel all report it, and they have to agree, so it is decided here once.
 */
public final class ProfileDisabledFeatures {

    private ProfileDisabledFeatures() {
    }

    public static List<FeatureType> of(ProfileManager profileManager) {
        List<FeatureType> disabled = new ArrayList<>(profileManager.featuresManager().getDisabledFeatures());
        if (!heapDumpReady(profileManager)) {
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

    /**
     * Only a heap dump profile has a heap dump, and its views have something in them only once the
     * index is built.
     */
    private static boolean heapDumpReady(ProfileManager profileManager) {
        if (profileManager.info().eventSource().kind() != ProfileKind.HEAP_DUMP) {
            return false;
        }
        HeapDumpManager heapDumpManager = profileManager.heapDumpManager();
        return heapDumpManager.heapDumpExists() && heapDumpManager.isCacheReady();
    }
}
