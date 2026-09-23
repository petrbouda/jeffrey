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
 * Top-consumer entry: total retained size grouped by ({@code packageName}, {@code classLoader}).
 *
 * @param packageName          package the classes belong to
 * @param classLoaderId        object id of the class loader that defined the classes (0 = bootstrap)
 * @param classLoaderClassName class name of the class loader
 * @param retainedSize         sum of retained sizes of all instances in this (package, loader) cell
 * @param shallowSize          sum of shallow sizes
 * @param classCount           distinct classes contributing to this cell
 * @param instanceCount        instance count across all classes in the cell
 */
public record ConsumerEntry(
        String packageName,
        long classLoaderId,
        String classLoaderClassName,
        long retainedSize,
        long shallowSize,
        int classCount,
        long instanceCount
) {
}
