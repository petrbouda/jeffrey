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
 * Summary information about a single class loader instance.
 *
 * @param objectId              the object ID of the class loader instance (0 for bootstrap class loader)
 * @param classLoaderClassName  the class name of the class loader (e.g. "java.net.URLClassLoader")
 * @param classCount            number of classes loaded by this class loader
 * @param totalClassSize        total shallow size of all classes loaded by this class loader
 * @param retainedSize          retained size of the class loader instance (0 if not computed)
 */
public record ClassLoaderInfo(
        long objectId,
        String classLoaderClassName,
        int classCount,
        long totalClassSize,
        long retainedSize
) {
}
