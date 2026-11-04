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

package cafe.jeffrey.profile.heapdump.oql.executor;

/**
 * The page of a query's rows a caller asks for: {@code limit} rows from the {@code offset}-th on.
 *
 * <p>Paged once, in {@link OqlExecutor}: every executor is asked for the rows up to the page's end
 * ({@link #fetch()}) — keeping its own way of telling that more exist beyond them — and the page is cut
 * out of what it returns. No executor needs to know about offsets, so none can forget one.
 */
public record PageWindow(int offset, int limit) {

    public PageWindow {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative: " + offset);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
    }

    /** The first page of {@code limit} rows. */
    public static PageWindow first(int limit) {
        return new PageWindow(0, limit);
    }

    /** How many rows the executors produce for this page: up to its end, saturating rather than overflowing. */
    int fetch() {
        long end = (long) offset + limit;
        return end > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) end;
    }
}
