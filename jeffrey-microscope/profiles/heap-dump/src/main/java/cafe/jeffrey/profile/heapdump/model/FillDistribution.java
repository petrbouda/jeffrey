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

/**
 * Distribution of collection fill ratios across buckets.
 *
 * @param empty  count of collections with 0% fill
 * @param low    count of collections with 1-25% fill
 * @param medium count of collections with 26-50% fill
 * @param high   count of collections with 51-75% fill
 * @param full   count of collections with 76-100% fill
 */
public record FillDistribution(
        int empty,
        int low,
        int medium,
        int high,
        int full
) {
}
