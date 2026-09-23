/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
package cafe.jeffrey.hub.model;

import java.nio.file.Path;

/**
 * The one rule for a path component that comes off the shared volume — a marker file another
 * pod wrote — and is resolved under a directory this hub then lists, serves and deletes: it
 * must stay inside that directory. Absolute, or climbing out through {@code ..}, it would point
 * {@code deleteSession} at whatever it named.
 */
public final class RelativePath {

    private static final String PARENT = "..";

    private RelativePath() {
    }

    public static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        require(Path.of(value), name);
        return value;
    }

    public static Path require(Path value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        if (value.isAbsolute()) {
            throw new IllegalArgumentException(name + " must be relative: " + value);
        }
        for (Path segment : value.normalize()) {
            if (PARENT.equals(segment.toString())) {
                throw new IllegalArgumentException(name + " must stay inside its directory: " + value);
            }
        }
        return value;
    }
}
