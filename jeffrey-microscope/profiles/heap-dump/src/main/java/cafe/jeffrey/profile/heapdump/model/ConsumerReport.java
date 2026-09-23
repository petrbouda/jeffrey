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

import java.util.List;

/**
 * Combined report containing two complementary "what is bloated?" views:
 * <ul>
 *   <li>{@code topConsumers} — retained size grouped by ({@code packageName}, {@code classLoader}).
 *       Answers "which subsystem is consuming the most memory?", with classloader as a separating
 *       dimension (so e.g. one webapp's beans don't get conflated with another's).</li>
 *   <li>{@code componentReport} — retained size rolled up purely by package, ignoring classloader.
 *       Answers "which Java package owns the most heap?".</li>
 * </ul>
 * Both lists are sorted by retained size descending and capped at the top entries.
 *
 * @param totalHeapSize    total heap size (corrected for compressed oops)
 * @param topConsumers     top (package, classloader) cells by retained size
 * @param componentReport  top packages by retained size
 */
public record ConsumerReport(
        long totalHeapSize,
        List<ConsumerEntry> topConsumers,
        List<ComponentEntry> componentReport
) {
}
