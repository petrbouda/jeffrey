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
 * Per-package roll-up for the component report.
 *
 * @param packageName     fully qualified package (e.g. {@code "org.springframework.beans"})
 * @param retainedSize    sum of retained sizes of all instances of classes in this package
 * @param shallowSize     sum of shallow sizes
 * @param classCount      distinct classes in this package present in the heap
 * @param instanceCount   total instance count across all classes in this package
 */
public record ComponentEntry(
        String packageName,
        long retainedSize,
        long shallowSize,
        int classCount,
        long instanceCount
) {
}
