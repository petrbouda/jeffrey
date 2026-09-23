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
 * One row of the "Roots by ClassLoader" view: how many GC roots reference
 * classes loaded by a given classloader, and what they retain.
 *
 * @param classloaderObjectId object id of the classloader instance, or
 *                            {@code null} for the bootstrap loader
 * @param classloaderClass    fully qualified class name of the loader, or
 *                            {@code "Bootstrap"} when {@code classloaderObjectId}
 *                            is {@code null}
 * @param rootCount           number of GC root rows whose rooted class was
 *                            loaded by this loader
 * @param totalRetainedBytes  sum of retained sizes across all those roots
 */
public record GCRootClassLoaderAggregate(
        Long classloaderObjectId,
        String classloaderClass,
        long rootCount,
        long totalRetainedBytes
) {
}
