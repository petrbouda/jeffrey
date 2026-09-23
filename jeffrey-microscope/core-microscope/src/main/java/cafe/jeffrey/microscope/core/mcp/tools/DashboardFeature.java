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

import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;

/**
 * Whether a profile carries the data one of the technology dashboards is built from.
 * <p>
 * Asked before every dashboard tool runs, because these managers answer an absent event type with a
 * well-formed empty result: without the check a profile that never recorded JDBC would report zero
 * statements and a perfect success rate, which reads as "the database is fine" rather than "nothing
 * was measured". The distinction is the finding.
 */
final class DashboardFeature {

    private DashboardFeature() {
    }

    static boolean missing(ProfileManager profileManager, FeatureType feature) {
        return profileManager.featuresManager().getDisabledFeatures().contains(feature);
    }
}
