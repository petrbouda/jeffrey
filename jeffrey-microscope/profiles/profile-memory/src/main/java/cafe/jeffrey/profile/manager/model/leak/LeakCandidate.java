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

package cafe.jeffrey.profile.manager.model.leak;

/**
 * One leak-candidate sample, from a {@code jdk.OldObjectSample} event — a live object that survived
 * long enough to be flagged as a potential leak.
 *
 * @param className              class of the leaked object
 * @param objectSizeBytes        shallow size of the object
 * @param objectAgeNanos         how long the object has been alive
 * @param arrayElements          element count when the object is an array (0 otherwise)
 * @param lastKnownHeapUsageBytes heap usage when the sample was taken
 */
public record LeakCandidate(
        String className,
        long objectSizeBytes,
        long objectAgeNanos,
        int arrayElements,
        long lastKnownHeapUsageBytes) {
}
