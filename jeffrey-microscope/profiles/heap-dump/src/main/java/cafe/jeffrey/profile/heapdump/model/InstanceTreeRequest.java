/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.heapdump.model;

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
