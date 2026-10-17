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

/**
 * The on-disk layout contract of the shared workspaces filesystem.
 *
 * <p>The provisioner (writer) creates this structure on the shared volume and
 * jeffrey-hub (reader) reads projects, instances and sessions from it whenever it is asked —
 * it keeps no copy of its own:</p>
 *
 * <pre>
 * &lt;workspaces&gt;/
 *   &lt;workspace-ref-id&gt;/
 *     &lt;project-name&gt;/
 *       .project-info.json
 *       &lt;instance-id&gt;/
 *         .instance-info.json
 *         &lt;session-id&gt;/
 *           .session-info.json
 *           .heartbeat/                   heartbeat liveness files
 * </pre>
 *
 * <p>Both sides must resolve these names from this single class; any rename
 * silently desynchronizes producer and consumer otherwise. The heartbeat
 * directory and file names live in {@link HeartbeatConstants} because the
 * zero-dependency agent duplicates that subset.</p>
 */
public abstract class JeffreyLayout {

    /** Directory under the Jeffrey home that holds all workspaces */
    public static final String WORKSPACES_DIR = "workspaces";

    /** Project metadata marker file inside a project directory */
    public static final String PROJECT_INFO_FILE = ".project-info.json";

    /** Instance metadata marker file inside an instance directory */
    public static final String INSTANCE_INFO_FILE = ".instance-info.json";

    /** Session metadata marker file inside a session directory */
    public static final String SESSION_INFO_FILE = ".session-info.json";
}
