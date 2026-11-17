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

package cafe.jeffrey.profile.manager.thread;

import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData;
import cafe.jeffrey.microscope.model.ProfileInfo;

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
