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

package cafe.jeffrey.microscope.model;

public enum StacktraceTag {
    EXCLUDE_IDLE(0, false),
    UNSAFE_ALLOCATION(1, true);

    private static final StacktraceTag[] VALUES = values();

    private final int id;
    private final boolean includes;

    /**
     * @param id       ID of the tag to optimize the space in DB and avoid storing duplicated strings
     * @param includes tag includes or excludes records from the database (mapped to IN or NOT IN clause)
     */
    StacktraceTag(int id, boolean includes) {
        this.id = id;
        this.includes = includes;
    }

    public int id() {
        return id;
    }

    public boolean includes() {
        return includes;
    }
}
