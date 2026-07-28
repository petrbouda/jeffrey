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
