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

package cafe.jeffrey.jib.payload;

import com.google.cloud.tools.jib.api.buildplan.FilePermissions;

/** The two file modes payloads are installed with. */
public abstract class PayloadPermissions {

    private static final String EXECUTABLE_MODE = "755";
    private static final String READABLE_MODE = "644";

    /** The native provisioner, which the entrypoint runs directly. */
    public static final FilePermissions EXECUTABLE = FilePermissions.fromOctalString(EXECUTABLE_MODE);

    /**
     * Everything else. The provisioner jar is read by a JVM and async-profiler is loaded with
     * {@code dlopen}; neither needs the executable bit.
     */
    public static final FilePermissions READABLE = FilePermissions.fromOctalString(READABLE_MODE);
}
