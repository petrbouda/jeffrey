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
