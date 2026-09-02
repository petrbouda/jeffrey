/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.shared.common;

public abstract class CacheKey {
    public static final String PROFILE_AUTO_ANALYSIS = "profileAutoAnalysis";
    public static final String PROFILE_CONFIGURATION = "profileConfiguration";
    // Suffixed when the cached shape changes: an entry written by an older version would no longer
    // deserialize, and the cache has no other way to tell the two apart.
    public static final String PROFILE_THREAD = "profileThreadBands";
    public static final String PROFILE_VIEWER = "profileViewer";
    public static final String PROFILE_EVENT_SUMMARY = "profileEventSummary";
    public static final String PROFILE_ACTIVE_SETTINGS = "profileActiveSettings";
}
