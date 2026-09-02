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

package cafe.jeffrey.profile.manager.action;

import cafe.jeffrey.profile.manager.ProfileManager;

import java.util.concurrent.CompletableFuture;

/**
 * Warms the profile views that are expensive to compute and cheap to keep: the thread viewer's
 * and the Thread Viewer's bands. Both are cached in the profile's own database, so this only ever
 * decides <em>when</em> the work happens, never whether the view is available.
 * <p>
 * That is why it does not block the profile from opening. The events are queryable before this
 * starts, and everything these views need is already written; warming them first only meant every
 * user waited for a thread-viewer frame tree before they could look at a flamegraph.
 */
public interface ProfileDataInitializer {

    /**
     * Starts warming the profile's cached views and returns immediately.
     *
     * @return completes when every view has been warmed, or completes exceptionally if one failed.
     * Callers that merely want the profile usable can ignore it; tests and progress reporting await it.
     */
    CompletableFuture<Void> initialize(ProfileManager profileManager);
}
