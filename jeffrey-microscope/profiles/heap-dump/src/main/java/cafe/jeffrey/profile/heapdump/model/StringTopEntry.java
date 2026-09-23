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
 * One row in the "Top Strings by Retained Size" report — strings grouped by
 * decoded content, ranked by how much heap they occupy in total.
 *
 * <p>{@code retainedSize} sums the shallow size of every {@code String} wrapper
 * with this content plus the shallow size of each distinct backing {@code byte[]}
 * array, so it reflects what the heap would shrink by if every instance of this
 * content were removed.
 *
 * @param content          the decoded string content (truncated if very long)
 * @param count            number of String instances with this content
 * @param arrayShallowSize shallow size of one backing {@code byte[]} array, in bytes
 *                         (constant across all arrays for the same content)
 * @param retainedSize     total bytes occupied: sum of String shallow sizes +
 *                         (distinct backing arrays) × arrayShallowSize
 */
public record StringTopEntry(
        String content,
        int count,
        long arrayShallowSize,
        long retainedSize
) {
}
