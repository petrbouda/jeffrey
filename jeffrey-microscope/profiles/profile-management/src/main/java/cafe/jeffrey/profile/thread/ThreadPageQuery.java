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
