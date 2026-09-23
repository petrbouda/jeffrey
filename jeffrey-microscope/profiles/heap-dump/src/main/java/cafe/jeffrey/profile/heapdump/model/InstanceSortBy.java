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
 * Sort criteria for the per-class instance browser.
 */
public enum InstanceSortBy {
    /**
     * Sort by raw instance id (ascending). Stable order; no dominator tree required.
     */
    OBJECT_ID,

    /**
     * Sort by retained size (descending, NULLs last). Requires the dominator tree.
     */
    RETAINED_SIZE
}
