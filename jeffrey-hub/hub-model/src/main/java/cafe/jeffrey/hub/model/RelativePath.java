/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
