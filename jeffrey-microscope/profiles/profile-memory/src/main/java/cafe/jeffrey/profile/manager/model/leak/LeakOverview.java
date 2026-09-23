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
 * Headline leak-candidate metrics.
 *
 * @param candidateCount  number of {@code jdk.OldObjectSample} candidates
 * @param largestBytes    size of the largest single candidate
 * @param totalBytes      summed size of all candidates
 * @param oldestAgeNanos  age of the longest-lived candidate
 */
public record LeakOverview(int candidateCount, long largestBytes, long totalBytes, long oldestAgeNanos) {
}
