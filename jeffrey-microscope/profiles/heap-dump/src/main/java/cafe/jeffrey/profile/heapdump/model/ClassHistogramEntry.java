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

import java.util.List;

/**
 * Represents a single entry in a class histogram.
 *
 * @param className     fully qualified class name
 * @param instanceCount number of instances of this class
 * @param totalSize     total memory used by all instances in bytes
 * @param referrers     top referrer class hints — only populated for opaque
 *                      primitive-array rows (e.g. {@code byte[]}) where the
 *                      class name alone doesn't say what the bytes are for;
 *                      empty list otherwise
 */
public record ClassHistogramEntry(
        String className,
        long instanceCount,
        long totalSize,
        List<ReferrerSummary> referrers
) {
}
