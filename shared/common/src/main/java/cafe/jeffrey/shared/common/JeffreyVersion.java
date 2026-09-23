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
