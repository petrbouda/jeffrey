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

package cafe.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * Per-loader unloadability diagnostic. The verdict explains whether the
 * loader could be garbage-collected on the next metaspace-aware GC cycle and,
 * for pinned cases, names the classes whose live instances are responsible.
 *
 * @param verdict             unloadability outcome for the loader
 * @param liveInstanceCount   total instances of every class loaded by this loader (excluding the loader itself)
 * @param rooted              {@code true} when the loader is <em>effectively</em> rooted — either
 *                            directly listed as a GC root in the HPROF dump, or reachable from a
 *                            rooted loader by walking up its {@code parent} chain
 * @param topBlockingClasses  top classes whose live instances pin the loader; empty for non-pinned verdicts
 */
public record ClassLoaderUnloadability(
        UnloadabilityVerdict verdict,
        long liveInstanceCount,
        boolean rooted,
        List<BlockingClass> topBlockingClasses) {
}
