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

/**
 * One class defined by a class loader, with its instance footprint. Renders
 * as a row in the Classes sub-tab of the Loader Detail drawer.
 *
 * <p>Intentionally omits per-class retained size: summing
 * {@code retained_size.bytes} across all instances of one class double-counts
 * any dominator subtree that nests another instance of the same class
 * (tries, linked lists, trees), routinely producing totals larger than the
 * whole heap. Per-loader retained size — which has a single, well-defined
 * meaning — is reported in the drawer header instead.
 */
public record ClassEntry(
        long classId,
        String name,
        long instanceCount,
        long totalInstanceSize) {
}
