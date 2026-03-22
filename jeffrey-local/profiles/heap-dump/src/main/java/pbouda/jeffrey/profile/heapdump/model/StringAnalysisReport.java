/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * Complete report for string deduplication analysis in a heap dump.
 * Analyzes Java 9+ compact strings (byte[] value + coder field).
 *
 * @param totalStrings           total String instances in the heap
 * @param totalStringShallowSize total shallow size of all String instances in bytes
 * @param uniqueArrays           number of unique byte[] arrays backing strings
 * @param sharedArrays           number of arrays shared by multiple Strings (deduplication active)
 * @param totalSharedStrings     total Strings sharing arrays (benefiting from deduplication)
 * @param memorySavedByDedup     bytes saved by current deduplication
 * @param potentialSavings       additional bytes that could be saved by deduplication
 * @param alreadyDeduplicated    list of strings already sharing arrays (sorted by savings desc)
 * @param opportunities          list of deduplication opportunities (sorted by potential savings desc)
 * @param jvmFlags               JVM flags related to string handling extracted from JFR events
 */
public record StringAnalysisReport(
        long totalStrings,
        long totalStringShallowSize,
        long uniqueArrays,
        long sharedArrays,
        long totalSharedStrings,
        long memorySavedByDedup,
        long potentialSavings,
        List<StringDeduplicationEntry> alreadyDeduplicated,
        List<StringDeduplicationEntry> opportunities,
        List<JvmStringFlag> jvmFlags
) {
}
