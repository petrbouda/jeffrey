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

package cafe.jeffrey.shared.common;

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.util.Optional;

public abstract class JeffreyVersion {
    private static final String JEFFREY_VERSION = "jeffrey-tag.txt";
    private static final String NO_VERSION = "Unknown";

    public static void print() {
        System.out.println(resolveJeffreyVersion());
    }

    /**
     * The version this build carries, or empty when the build stamped none — a development
     * run from the classes directory rather than a packaged release. Callers that print or
     * report the version want the {@code Unknown} of {@link #resolveJeffreyVersion()}; a caller
     * that would name a directory or a release after it wants to know there is none.
     */
    public static Optional<String> version() {
        String version = resolveJeffreyVersion();
        return NO_VERSION.equals(version) ? Optional.empty() : Optional.of(version);
    }

    public static String resolveJeffreyVersion() {
        try {
            String version = FileSystemUtils.readFromClasspath("classpath:" + JEFFREY_VERSION).strip();
            if (version.isBlank()) {
                return NO_VERSION;
            } else {
                return version;
            }
        } catch (Exception ex) {
            return NO_VERSION;
        }
    }
}
