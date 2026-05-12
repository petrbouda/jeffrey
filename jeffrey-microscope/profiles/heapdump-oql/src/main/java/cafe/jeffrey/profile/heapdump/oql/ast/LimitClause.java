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
package cafe.jeffrey.profile.heapdump.oql.ast;

/**
 * {@code LIMIT n [OFFSET m]} — produced when the user supplies an explicit
 * limit. When absent, the executor applies the request's effective limit
 * (currently {@code MAX_QUERY_LIMIT=100} in {@code HeapDumpManagerImpl}).
 */
public record LimitClause(long limit, long offset) {

    public LimitClause {
        if (limit < 0) {
            throw new IllegalArgumentException("LIMIT must not be negative: " + limit);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("OFFSET must not be negative: " + offset);
        }
    }
}
