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
 * Information about a class that is loaded by multiple class loaders,
 * which may indicate a class loader leak or misconfiguration.
 *
 * @param className        the fully qualified class name loaded by multiple loaders
 * @param loaderCount      number of distinct class loaders that loaded this class
 * @param classLoaderNames list of class loader class names that loaded this class
 */
public record DuplicateClassInfo(
        String className,
        int loaderCount,
        List<String> classLoaderNames
) {
}
