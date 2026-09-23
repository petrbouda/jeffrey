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
 * A single entry in the biggest objects report representing one large object in the heap.
 *
 * @param className    fully qualified class name of the object
 * @param shallowSize  shallow size in bytes
 * @param retainedSize retained size in bytes
 * @param objectId     object ID in the heap dump
 */
public record BiggestObjectEntry(
        String className,
        long shallowSize,
        long retainedSize,
        long objectId
) {
}
