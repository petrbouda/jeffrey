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

import java.time.Instant;

/**
 * Summary statistics from a Java heap dump.
 *
 * @param totalBytes     total size of all live objects in bytes
 * @param totalInstances total number of live object instances
 * @param classCount     number of loaded classes
 * @param gcRootCount    number of GC roots
 * @param timestamp      when the heap dump was taken
 */
public record HeapSummary(
        long totalBytes,
        long totalInstances,
        int classCount,
        int gcRootCount,
        Instant timestamp
) {
}
