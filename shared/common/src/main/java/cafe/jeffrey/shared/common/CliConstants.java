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
     * The session's recording file, {@code <session>/rec-%t<offset>.jfr}: async-profiler writes
     * {@code %t} in local time, and the machine's UTC offset after it is what every reader turns
     * the name back into an instant with.
     */
    public static final String FILE_PATTERN = "<<JEFFREY:FILE_PATTERN>>";

    /**
     * The async-profiler agent options a session runs with when no {@code profiler-command} is
     * configured. Options only: the library they are passed to is the one {@code profiler-path} names.
     */
    public static final String DEFAULT_PROFILER_OPTIONS =
            "start,alloc,lock,event=ctimer,jfrsync=default,loop=15m,chunksize=5m,file=" + FILE_PATTERN;

    /**
     * Reference id of the workspace used when {@code project.workspace-ref-id} is not set in the
     * provisioner's HOCON config: the provisioner lays the project down under
     * <code>&lt;workspaces&gt;/$default/&lt;project&gt;/...</code>, creating that workspace's
     * directory when it is missing. Nothing else creates a workspace.
     */
    public static final String DEFAULT_WORKSPACE_REF_ID = "$default";
}
