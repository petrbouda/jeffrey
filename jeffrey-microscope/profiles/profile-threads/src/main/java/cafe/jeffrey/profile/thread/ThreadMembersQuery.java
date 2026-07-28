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
