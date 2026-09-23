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
 * Asks for the threads behind one collapsed lane, a page at a time — opening a pool of 351 must not
 * put 351 lanes on the page any more than the ungrouped timeline could.
 *
 * @param groupKey which lane was opened; see {@link ThreadGroupKey}
 * @param sort     the order members are handed out in
 * @param offset   how many members to skip
 * @param limit    how many members to send
 */
public record ThreadMembersQuery(String groupKey, ThreadSort sort, int offset, int limit) {

    public ThreadMembersQuery {
        if (groupKey == null || groupKey.isBlank()) {
            throw new IllegalArgumentException("Group key must be specified");
        }
        if (sort == null) {
            throw new IllegalArgumentException("Sort must be specified");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative: " + offset);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive: " + limit);
        }
    }
}
