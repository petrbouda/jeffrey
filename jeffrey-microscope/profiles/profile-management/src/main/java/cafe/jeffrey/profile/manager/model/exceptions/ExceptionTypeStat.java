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

package cafe.jeffrey.profile.manager.model.exceptions;

import java.util.List;

/**
 * Aggregated throw statistics for one exception class, derived from {@code jdk.JavaExceptionThrow}
 * and {@code jdk.JavaErrorThrow} events.
 *
 * @param thrownClass binary name of the thrown class
 * @param count       number of sampled throws of this class
 * @param error       whether the class was recorded as an {@code Error} throw
 * @param messages    distinct exception messages with their occurrence counts (may be empty)
 * @param threadCount number of distinct threads that threw this class
 */
public record ExceptionTypeStat(
        String thrownClass,
        long count,
        boolean error,
        List<ExceptionMessageCount> messages,
        int threadCount) {
}
