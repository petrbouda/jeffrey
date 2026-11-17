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

package cafe.jeffrey.provider.profile.api;

/**
 * One trace type, aggregated over every trace of it.
 *
 * @param notificationCount       how many notifications the application raised inside traces of this
 *                                type, of any severity
 * @param urgentNotificationCount how many of those were {@code CRITICAL} or {@code HIGH}
 */
public record TraceOperationRecord(
        String name,
        String kind,
        String eventType,
        long count,
        long errorCount,
        long notificationCount,
        long urgentNotificationCount,
        long spanCount,
        long totalNanos,
        long p50Nanos,
        long p95Nanos,
        long p99Nanos,
        long maxNanos) {
}
