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

package cafe.jeffrey.profile.heapdump.model;

/**
 * Per-class delta between two heap dumps (primary vs baseline).
 *
 * @param className     the class both sides are keyed on
 * @param primaryCount  instance count in the primary dump (0 = class absent)
 * @param baselineCount instance count in the baseline dump (0 = class absent)
 * @param countDelta    {@code primaryCount - baselineCount}
 * @param primaryBytes  shallow bytes in the primary dump
 * @param baselineBytes shallow bytes in the baseline dump
 * @param bytesDelta    {@code primaryBytes - baselineBytes}
 */
public record ClassDiffEntry(
        String className,
        long primaryCount,
        long baselineCount,
        long countDelta,
        long primaryBytes,
        long baselineBytes,
        long bytesDelta
) {
}
