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
 * One row in the "Largest String Instances" report — a single {@code String}
 * object on the heap, ranked by its GC-retained size.
 *
 * <p>{@code retainedSize} is sharing-aware: it equals the String wrapper's
 * shallow size plus the backing {@code byte[]} shallow size <em>only when this
 * String is the sole referrer of that array</em>. Strings whose backing array
 * is shared with other Strings retain only their wrapper bytes — removing one
 * of them would not free the array.
 *
 * @param content          decoded and truncated string preview. Always non-null:
 *                         the analyzer re-decodes a bounded prefix from the
 *                         heap dump for Strings whose content exceeded the
 *                         indexer's cap, so the cost stays sub-kilobyte even
 *                         for multi-megabyte backing arrays. Empty only when
 *                         the underlying String can't be decoded at all.
 * @param instanceId       heap object id of the String instance
 * @param arrayShallowSize shallow size of the backing {@code byte[]} array
 * @param arrayRefCount    number of String instances that reference this
 *                         backing array (1 = unique, &gt; 1 = shared)
 * @param retainedSize     bytes the heap would shrink by if this single String
 *                         were removed
 */
public record StringInstanceEntry(
        String content,
        long instanceId,
        long arrayShallowSize,
        int arrayRefCount,
        long retainedSize
) {
}
