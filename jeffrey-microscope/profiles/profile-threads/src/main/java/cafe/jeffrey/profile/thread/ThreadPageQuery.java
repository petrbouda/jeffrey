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

/**
 * Which slice of the timeline to send.
 *
 * <p>The name filter is applied here rather than in the browser on purpose: with only a page of
 * threads loaded, a filter that ran on the client would search the page instead of the recording,
 * and a thread that matched would stay hidden simply because it sorted low.
 *
 * @param sort       the order threads are handed out in
 * @param nameFilter case-insensitive substring the thread name must contain; blank matches everything
 * @param offset     how many threads to skip
 * @param limit      how many threads to send
 */
public record ThreadPageQuery(ThreadSort sort, String nameFilter, int offset, int limit) {

    public ThreadPageQuery {
        if (sort == null) {
            throw new IllegalArgumentException("Sort must be specified");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative: " + offset);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive: " + limit);
        }
        nameFilter = nameFilter == null ? "" : nameFilter.trim();
    }

    public boolean hasNameFilter() {
        return !nameFilter.isEmpty();
    }
}
