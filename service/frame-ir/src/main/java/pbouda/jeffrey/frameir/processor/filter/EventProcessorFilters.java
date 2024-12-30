/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.frameir.processor.filter;

import pbouda.jeffrey.common.config.Config;

public abstract class EventProcessorFilters {

    public static final EventProcessorFilter EXCLUDE_NULL_STACKTRACE = new ExcludeNullStacktraceFilter();

    public static EventProcessorFilter resolveFilters(Config config) {
        EventProcessorFilter chain = EXCLUDE_NULL_STACKTRACE;
        if (config.threadInfo() != null) {
            chain = chain.and(new IncludeSingleThreadOnlyFilter(config.threadInfo()));
        }
        if (config.graphParameters().excludeIdleSamples()) {
            chain = chain.and(new ExcludeIdleSamplesFilter());
        }
        if (config.graphParameters().excludeNonJavaSamples()) {
            chain = chain.and(new ExcludeNonJavaSamplesFilter());
        }
        if (config.graphParameters().onlyUnsafeAllocationSamples()) {
            chain = chain.and(new OnlyUnsafeAllocationSamplesFilter());
        }
        // If only EXCLUDE_NULL_STACKTRACE is present, we can return it directly and avoid caching
        if (chain == EXCLUDE_NULL_STACKTRACE) {
            return chain;
        } else {
            return new CachingFilter(chain);
        }
    }
}
