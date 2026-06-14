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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.function.Function;

/**
 * Virtual-thread (Project Loom) insight for a single profile: pinning
 * ({@code jdk.VirtualThreadPinned}), carrier-submit failures ({@code jdk.VirtualThreadSubmitFailed}),
 * and thread lifecycle ({@code jdk.VirtualThreadStart}/{@code jdk.VirtualThreadEnd}). The lifecycle
 * events are disabled by default, so consumers must handle an empty lifecycle.
 */
public interface VirtualThreadManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, VirtualThreadManager> {
    }

    /**
     * Composite virtual-thread dashboard data: pinning timeline/distribution/top-threads,
     * submit failures, and the lifecycle timeline.
     */
    VirtualThreadData virtualThreadData();
}
