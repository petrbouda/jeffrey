/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

/**
 * The on-disk layout contract of the shared workspaces filesystem.
 *
 * <p>The provisioner (writer) creates this structure on the shared volume and
 * jeffrey-hub (reader) discovers projects, instances and sessions from it:</p>
 *
 * <pre>
 * &lt;workspaces&gt;/
 *   .events/                              CLI-to-hub folder queue
 *   &lt;workspace-ref-id&gt;/
 *     .settings/settings-&lt;timestamp&gt;.json hub-pushed profiler settings
 *     &lt;project-name&gt;/
 *       .project-info.json
 *       &lt;instance-id&gt;/
 *         .instance-info.json
 *         &lt;session-id&gt;/
 *           .session-info.json
 *           streaming-repo/               JFR streaming repository
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

    /** Folder-queue directory under the workspaces dir for CLI-to-hub events */
    public static final String EVENTS_DIR = ".events";

    /** Directory inside a session directory holding the JFR streaming repository */
    public static final String STREAMING_REPO_DIR = "streaming-repo";

    /** Directory under a workspace dir holding hub-pushed profiler settings files */
    public static final String SETTINGS_DIR = ".settings";

    /** Filename prefix of profiler settings files inside {@link #SETTINGS_DIR} */
    public static final String SETTINGS_FILE_PREFIX = "settings-";

    /** Timestamp pattern embedded in profiler settings filenames (UTC) */
    public static final String SETTINGS_TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HHmmssSSSSSS";

    /** Project metadata marker file inside a project directory */
    public static final String PROJECT_INFO_FILE = ".project-info.json";

    /** Instance metadata marker file inside an instance directory */
    public static final String INSTANCE_INFO_FILE = ".instance-info.json";

    /** Session metadata marker file inside a session directory */
    public static final String SESSION_INFO_FILE = ".session-info.json";
}
