/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.model;

/**
 * Request parameters for fetching instance tree data.
 *
 * @param objectId the object ID to get referrers or reachables for
 * @param mode     the tree mode (REFERRERS or REACHABLES)
 * @param limit    maximum number of children to return
 * @param offset   offset for pagination
 */
public record InstanceTreeRequest(
        long objectId,
        TreeMode mode,
        int limit,
        int offset
) {
    /**
     * The mode of tree navigation.
     */
    public enum TreeMode {
        /**
         * Show objects that reference the target instance.
         */
        REFERRERS,
        /**
         * Show objects that the target instance references.
         */
        REACHABLES
    }

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;

    /**
     * Create a request with default pagination.
     */
    public static InstanceTreeRequest referrers(long objectId) {
        return new InstanceTreeRequest(objectId, TreeMode.REFERRERS, DEFAULT_LIMIT, DEFAULT_OFFSET);
    }

    /**
     * Create a request with default pagination.
     */
    public static InstanceTreeRequest reachables(long objectId) {
        return new InstanceTreeRequest(objectId, TreeMode.REACHABLES, DEFAULT_LIMIT, DEFAULT_OFFSET);
    }

    /**
     * Create a request with custom pagination.
     */
    public static InstanceTreeRequest referrers(long objectId, int limit, int offset) {
        return new InstanceTreeRequest(objectId, TreeMode.REFERRERS, limit, offset);
    }

    /**
     * Create a request with custom pagination.
     */
    public static InstanceTreeRequest reachables(long objectId, int limit, int offset) {
        return new InstanceTreeRequest(objectId, TreeMode.REACHABLES, limit, offset);
    }
}
