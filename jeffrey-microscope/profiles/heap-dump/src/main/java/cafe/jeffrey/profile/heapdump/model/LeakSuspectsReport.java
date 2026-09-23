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
 * Report containing identified leak suspects with dominator-cluster analysis results.
 *
 * @param totalHeapSize           total heap size in bytes
 * @param analyzedBytes           total bytes analyzed (sum of suspect retained sizes)
 * @param suspects                list of leak suspects ordered by rank (lowest = most suspicious)
 * @param topLeakingClassLoaders  per-class-loader aggregate across all suspects, ordered by total retained size
 *                                (includes bootstrap; UI is expected to filter bootstrap by default)
 */
public record LeakSuspectsReport(
        long totalHeapSize,
        long analyzedBytes,
        List<LeakSuspect> suspects,
        List<ClassLoaderLeakSummary> topLeakingClassLoaders
) {
}
