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

package cafe.jeffrey.profile.manager.model.io;

/**
 * How an endpoint's I/O is measured: by the volume it moved, or by how often it was called.
 * <p>
 * The distinction matters because it decides which endpoints are even worth showing. The heaviest
 * peers by bytes and the busiest peers by call count are frequently disjoint sets — a cache or a
 * message broker can dominate the call count while moving a rounding error's worth of bytes — so
 * the metric has to reach the ranking, not just the labels.
 */
public enum IoMetric {
    BYTES,
    COUNT
}
