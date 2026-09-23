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
 * One group of byte-identical primitive arrays.
 *
 * @param typeName        array type name (e.g. {@code byte[]})
 * @param arrayLength     element count of every array in the group
 * @param count           number of byte-identical instances
 * @param shallowSize     shallow size of a single instance
 * @param wastedBytes     {@code (count - 1) * shallowSize} — reclaimable by sharing one copy
 * @param contentPreview  short human-readable preview of the shared content
 * @param sampleObjectIds a few instance ids for drill-down
 */
public record DuplicateArrayGroup(
        String typeName,
        int arrayLength,
        int count,
        long shallowSize,
        long wastedBytes,
        String contentPreview,
        List<Long> sampleObjectIds
) {
}
