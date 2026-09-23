/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
 * Represents a single entry in string deduplication analysis.
 * Used for both already-deduplicated strings and deduplication opportunities.
 *
 * @param content   the string content (truncated if very long)
 * @param count     number of String instances with this content/array
 * @param arraySize size of the backing byte[] array in bytes
 * @param savings   bytes saved (for deduplicated) or could be saved (for opportunities)
 */
public record StringDeduplicationEntry(
        String content,
        int count,
        long arraySize,
        long savings
) {
}
