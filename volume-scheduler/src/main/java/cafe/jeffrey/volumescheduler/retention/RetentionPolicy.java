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

package cafe.jeffrey.volumescheduler.retention;

import java.time.Duration;

/**
 * What the retention job keeps of every project, rule by rule in the order they run.
 *
 * @param sessionRetention           a session goes whole once its newest recording started longer
 *                                   ago than this ({@link SessionRetentionRule})
 * @param maxSessions                logical sessions kept per instance, a run of failed-empty ones
 *                                   counting as one; the oldest beyond it go ({@link SessionRetentionRule})
 * @param newestSessionFileRetention each instance's newest session loses its closed recordings older
 *                                   than this ({@link NewestSessionTrimRule})
 * @param maxProjectBytes            the disk one project may occupy, reclaimed oldest first
 *                                   ({@link StorageQuotaRule})
 * @param instanceRetention          an instance directory without a session goes once it is older
 *                                   than this ({@link StaleInstanceRule})
 */
public record RetentionPolicy(
        Duration sessionRetention,
        int maxSessions,
        Duration newestSessionFileRetention,
        long maxProjectBytes,
        Duration instanceRetention) {

    public RetentionPolicy {
        requirePositive("session-retention", sessionRetention);
        requirePositive("newest-session-file-retention", newestSessionFileRetention);
        requirePositive("instance-retention", instanceRetention);
        if (maxSessions <= 0) {
            throw new IllegalArgumentException("max-sessions must be positive: " + maxSessions);
        }
        if (maxProjectBytes <= 0) {
            throw new IllegalArgumentException("max-size must be positive: " + maxProjectBytes);
        }
    }

    private static void requirePositive(String name, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive: " + duration);
        }
    }
}
