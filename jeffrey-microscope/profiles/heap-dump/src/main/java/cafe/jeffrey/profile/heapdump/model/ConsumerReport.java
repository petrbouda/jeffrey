/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
