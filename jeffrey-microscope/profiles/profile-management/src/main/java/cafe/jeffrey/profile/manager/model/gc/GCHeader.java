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

package cafe.jeffrey.profile.manager.model.gc;

import java.math.BigDecimal;

public record GCHeader(
    int totalCollections,
    int youngCollections,
    int oldCollections,
    int fullCollections,
    long maxPauseTime,
    long p95PauseTime,
    long p99PauseTime,
    long totalMemoryFreed,
    long avgMemoryFreed,
    BigDecimal gcThroughput,
    BigDecimal gcOverhead,
    long totalGcTime,
    BigDecimal collectionFrequency,
    ManualGCCalls manualGCCalls
) {}
