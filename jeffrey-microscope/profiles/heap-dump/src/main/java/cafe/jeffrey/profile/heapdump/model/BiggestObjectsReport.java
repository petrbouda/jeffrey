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

import java.util.List;

/**
 * Report containing the biggest individual objects in the heap by retained size.
 *
 * @param totalHeapSize     total heap size in bytes
 * @param totalRetainedSize total retained size of the reported objects in bytes
 * @param entries           list of biggest object entries ordered by retained size descending
 */
public record BiggestObjectsReport(
        long totalHeapSize,
        long totalRetainedSize,
        List<BiggestObjectEntry> entries
) {
}
