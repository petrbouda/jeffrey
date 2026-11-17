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

package cafe.jeffrey.profile.manager.model.gc.tuning;

import java.util.List;

/**
 * Promotion/tenuring insight for the GC deep-tuning tab.
 *
 * @param gcs per-collection survivor-age distributions, most recent collection first
 */
public record TenuringData(List<TenuringGcSummary> gcs) {

    /**
     * Survivor-age distribution of one garbage collection, from {@code jdk.TenuringDistribution}.
     *
     * @param gcId           collection identifier
     * @param totalSizeBytes total surviving bytes across all ages
     * @param buckets        per-age sizes, ascending by age
     */
    public record TenuringGcSummary(long gcId, long totalSizeBytes, List<TenuringAgeBucket> buckets) {
    }

    /**
     * Surviving bytes of one tenuring age within a single collection.
     */
    public record TenuringAgeBucket(int age, long sizeBytes) {
    }
}
