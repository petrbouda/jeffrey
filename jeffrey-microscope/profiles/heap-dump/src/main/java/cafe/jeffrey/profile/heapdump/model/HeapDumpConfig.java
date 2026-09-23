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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Persisted configuration resolved during heap dump initialization.
 *
 * @param compressedOops       whether compressed oops are enabled
 * @param compressedOopsSource how compressed oops was determined: MANUAL, JFR, or INFERRED
 * @param totalOvercount       heap-wide reference size overcount (only meaningful when compressedOops is true)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HeapDumpConfig(
        boolean compressedOops,
        String compressedOopsSource,
        long totalOvercount
) {
}
