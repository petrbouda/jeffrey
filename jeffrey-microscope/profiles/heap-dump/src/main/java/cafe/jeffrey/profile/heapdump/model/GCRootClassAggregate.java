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
package cafe.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * One row of the "Roots by Class" view: how many GC roots reference an
 * instance of {@code className}, what kinds of roots they are, and what they
 * retain in total.
 *
 * @param className          fully qualified class name
 * @param rootCount          number of GC root rows pointing at instances of this class
 * @param rootKinds          distinct HPROF root-kind labels seen for this class,
 *                           ordered by frequency descending
 * @param totalRetainedBytes sum of retained sizes across all rooted instances
 *                           of this class
 */
public record GCRootClassAggregate(
        String className,
        long rootCount,
        List<String> rootKinds,
        long totalRetainedBytes
) {
}
