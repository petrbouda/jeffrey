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

package cafe.jeffrey.frameir;

import java.util.regex.Pattern;

/**
 * What two runs' frames are matched by when they are compared: the frame's name, but for the part of a
 * native library's path that names nothing but the run.
 *
 * <p>A native library a jar unpacks before it loads it — the SQLite driver's, say — lands in a temporary
 * file with a fresh UUID in its name each run ({@code /tmp/sqlite-3.53.4.0-<uuid>-libsqlitejdbc.so}),
 * and a frame no symbol resolved is named by that path. Matched by its name, the same library is a frame
 * one run added and the other removed; matched by its key, it is one frame. Only the match uses the key:
 * every graph still shows the frame by its own name.
 */
final class FrameMatchKey {

    /** A path is a native library's: Java frames never carry a slash in their name. */
    private static final String PATH_PREFIX = "/";

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** What stands for the UUID in a key. */
    private static final String ANY_RUN = "*";

    private FrameMatchKey() {
    }

    /** The key a frame of this name is matched by: its name, a native library's path without its run's UUID. */
    static String of(String methodName) {
        if (methodName == null || !methodName.startsWith(PATH_PREFIX)) {
            return methodName;
        }
        return UUID.matcher(methodName).replaceAll(ANY_RUN);
    }
}
