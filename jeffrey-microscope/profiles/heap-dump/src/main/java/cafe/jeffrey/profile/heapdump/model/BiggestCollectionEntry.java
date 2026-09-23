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
 * A single entry representing one of the biggest individual collections in the heap.
 *
 * @param objectId       object ID of the collection instance in the heap dump
 * @param className      fully qualified class name of the collection (e.g., java.util.HashMap)
 * @param elementCount   number of elements stored in the collection
 * @param capacity       total capacity of the collection's backing array
 * @param fillRatio      ratio of elementCount to capacity (0.0 to 1.0)
 * @param shallowSize    shallow size of the collection instance in bytes
 * @param retainedSize   retained size of the collection instance in bytes
 * @param ownerClassName fully qualified class name of the object that references this collection, or null
 */
public record BiggestCollectionEntry(
        long objectId,
        String className,
        int elementCount,
        int capacity,
        double fillRatio,
        long shallowSize,
        long retainedSize,
        String ownerClassName
) {
}
