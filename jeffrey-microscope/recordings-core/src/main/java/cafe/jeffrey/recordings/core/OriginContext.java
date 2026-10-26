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

package cafe.jeffrey.recordings.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures the (remote, workspace, project) that a recording was downloaded from.
 * Used to materialise {@code origin.*} system tags on the freshly imported QA recording.
 *
 * @param remoteAddress the remote's address identity — which remote the copy came from, so a copy
 *                      taken from a remote configured before is never matched to the current one
 * @param remoteName    what the UI shows for it
 */
public record OriginContext(
        String remoteAddress,
        String remoteName,
        String workspaceId,
        String projectId,
        String projectName) {

    /**
     * Present on a recording that holds a window of its session rather than all of it. The
     * whole-session download keys on the other {@code origin.*} tags to say "already here"; a
     * window carries this one so that answer is never given about a part.
     */
    public static final String TAG_WINDOW = "origin.window";

    /**
     * Present on a whole-session copy taken once the session had finished. A copy of a session
     * still recording holds the chunks it had then, so only a copy carrying this is handed back
     * as "already here" instead of downloading the session again.
     */
    public static final String TAG_COMPLETE = "origin.complete";

    /**
     * When the remote session started (epoch millis). The recording's name shows it too, but a name is a
     * label nothing reads back: this is where the fact is kept.
     */
    public static final String TAG_SESSION_STARTED_AT = "origin.sessionStartedAt";

    /**
     * The instance (replica) whose session this is. Two replicas of a project can start a session in
     * the same second, and their copies are told apart here, not by their names.
     */
    public static final String TAG_INSTANCE_ID = "origin.instanceId";

    /** The keys a downloaded recording is matched back to its remote session by. */
    public static final String TAG_REMOTE_ADDRESS = "origin.remoteAddress";
    public static final String TAG_WORKSPACE_ID = "origin.workspaceId";
    public static final String TAG_PROJECT_ID = "origin.projectId";
    public static final String TAG_RECORDING_ID = "origin.recordingId";

    /** The keys the UI shows where a recording came from. */
    public static final String TAG_REMOTE = "origin.remote";
    public static final String TAG_WORKSPACE = "origin.workspace";
    public static final String TAG_PROJECT = "origin.project";

    /**
     * Builds the {@code origin.*} system tag map for a recording downloaded from an upstream session.
     *
     * @param upstreamRecordingId the upstream session/recording id (used for dedup of repeat downloads)
     */
    public Map<String, String> toTagMap(String upstreamRecordingId) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (remoteAddress != null) {
            tags.put(TAG_REMOTE_ADDRESS, remoteAddress);
        }
        if (remoteName != null) {
            tags.put(TAG_REMOTE, remoteName);
        }
        if (workspaceId != null) {
            tags.put(TAG_WORKSPACE_ID, workspaceId);
            // A remote workspace is named by its directory on the volume, so its id is also what the UI shows
            tags.put(TAG_WORKSPACE, workspaceId);
        }
        if (projectId != null) {
            tags.put(TAG_PROJECT_ID, projectId);
        }
        if (projectName != null) {
            tags.put(TAG_PROJECT, projectName);
        }
        if (upstreamRecordingId != null) {
            tags.put(TAG_RECORDING_ID, upstreamRecordingId);
        }
        return tags;
    }
}
