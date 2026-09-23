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
package cafe.jeffrey.profile.heapdump.view;

/**
 * One row of a class histogram: per-class instance count and total shallow size.
 *
 * Produced by SQL aggregation against the {@code instance} table — the canonical
 * cheap query that motivates the SQL-pushdown design of the new index.
 *
 * {@code className} is null when an instance points to a class id that has no
 * {@code class} row (e.g. corrupt reference, primitive array which has no class
 * entry of its own).
 */
public record HistogramRow(
        Long classId,
        String className,
        long instanceCount,
        long totalShallowSize) {
}
