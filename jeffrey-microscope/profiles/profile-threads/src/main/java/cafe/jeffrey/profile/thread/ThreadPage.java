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

package cafe.jeffrey.profile.thread;

import java.util.List;

/**
 * One slice of a profile's threads, plus what the page needs to describe the rest of them.
 *
 * <p>Kept separate from {@link ThreadRoot}: the root is the whole timeline as it is computed and
 * cached, while this is the view of it that goes over the wire.
 *
 * @param common        the metadata every row is drawn against, unchanged between pages
 * @param rows          the threads in this slice, already ordered
 * @param offset        how many threads precede this slice
 * @param matchedCount  threads matching the current filter — what "showing 50 of N" counts
 * @param totalCount    threads in the recording, whether they matched the filter or not
 */
public record ThreadPage(
        ThreadCommon common,
        List<ThreadRow> rows,
        int offset,
        int matchedCount,
        int totalCount) {

    public boolean hasMore() {
        return offset + rows.size() < matchedCount;
    }
}
