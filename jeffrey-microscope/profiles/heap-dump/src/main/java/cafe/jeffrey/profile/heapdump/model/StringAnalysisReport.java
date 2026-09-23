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
 * @param topByRetained          top strings grouped by content, ranked by total retained size (sorted desc)
 * @param topInstancesByRetained top individual String instances, ranked by GC-retained size (sorted desc)
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
        List<StringTopEntry> topByRetained,
        List<StringInstanceEntry> topInstancesByRetained,
        List<StringDeduplicationEntry> alreadyDeduplicated,
        List<StringDeduplicationEntry> opportunities,
        List<JvmStringFlag> jvmFlags
) {
}
