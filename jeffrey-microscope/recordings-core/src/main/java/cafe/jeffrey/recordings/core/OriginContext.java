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
 * Captures the (hub, workspace, project) that a recording was downloaded from.
 * Used to materialise {@code origin.*} system tags on the freshly imported QA recording.
 */
public record OriginContext(
        String hubId,
        String hubName,
        String workspaceId,
        String projectId,
        String projectName) {

    /**
     * Present on a recording that holds a window of its session rather than all of it. The
     * whole-session download keys on the other {@code origin.*} tags to say "already here"; a
     * window carries this one so that answer is never given about a part.
     */
    public static final String TAG_WINDOW = "origin.window";

    /** The keys a downloaded recording is matched back to its hub session by. */
    public static final String TAG_HUB_ID = "origin.hubId";
    public static final String TAG_WORKSPACE_ID = "origin.workspaceId";
    public static final String TAG_PROJECT_ID = "origin.projectId";
    public static final String TAG_RECORDING_ID = "origin.recordingId";

    /** The keys the UI shows where a recording came from. */
    public static final String TAG_HUB = "origin.hub";
    public static final String TAG_WORKSPACE = "origin.workspace";
    public static final String TAG_PROJECT = "origin.project";

    /**
     * Builds the {@code origin.*} system tag map for a recording downloaded from an upstream session.
     *
     * @param upstreamRecordingId the upstream session/recording id (used for dedup of repeat downloads)
     */
    public Map<String, String> toTagMap(String upstreamRecordingId) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (hubId != null) {
            tags.put(TAG_HUB_ID, hubId);
        }
        if (hubName != null) {
            tags.put(TAG_HUB, hubName);
        }
        if (workspaceId != null) {
            tags.put(TAG_WORKSPACE_ID, workspaceId);
            // A hub workspace is named by its directory on the volume, so its id is also what the UI shows
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
