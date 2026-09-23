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

package cafe.jeffrey.shared.common;

public abstract class CliConstants {

    public static final String PROFILER_PATH = "<<JEFFREY:PROFILER_PATH>>";
    public static final String CURRENT_SESSION = "<<JEFFREY:CURRENT_SESSION>>";

    /**
     * The async-profiler agent options a session runs with when no {@code profiler-command} is
     * configured. Options only: the library they are passed to is the one {@code profiler-path} names.
     */
    public static final String DEFAULT_PROFILER_OPTIONS =
            "start,alloc,lock,event=ctimer,jfrsync=default,loop=15m,chunksize=5m,file="
                    + CURRENT_SESSION + "/profile-%t.jfr";

    /**
     * Reference id of the workspace used when {@code project.workspace-ref-id} is not set
     * in the provisioner's HOCON config. Both jeffrey-provisioner (when writing project metadata + the
     * filesystem layout) and jeffrey-hub (as the fallback for
     * {@code jeffrey.hub.default-workspace.reference-id}) resolve to this single
     * value so the two sides agree on the directory structure
     * <code>&lt;workspaces&gt;/$default/&lt;project&gt;/...</code>.
     */
    public static final String DEFAULT_WORKSPACE_REF_ID = "$default";
}
