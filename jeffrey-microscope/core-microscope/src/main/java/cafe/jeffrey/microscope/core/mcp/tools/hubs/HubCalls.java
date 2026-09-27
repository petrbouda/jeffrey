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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.profile.mcp.McpNextTool;

/**
 * The calls the {@code hubs_} answers route to, named once: the tools and the arguments they take, so
 * an answer says where to go next with values rather than retyping strings.
 */
public final class HubCalls {

    public static final String HUBS_LIST = "hubs_list";
    public static final String HUBS_SESSIONS = "hubs_sessions";
    public static final String HUBS_FILES = "hubs_files";
    public static final String HUBS_DOWNLOAD = "hubs_download";
    public static final String HUBS_FETCH_FILE = "hubs_fetchFile";
    public static final String OPERATIONS_STATUS = "operations_status";
    public static final String RECORDINGS_ANALYZE_RECORDING = "recordings_analyzeRecording";
    public static final String RECORDINGS_ANALYZE_FILE = "recordings_analyzeFile";
    public static final String PROFILES_SUMMARY = "profiles_summary";

    public static final String SESSION_REF = "sessionRef";
    public static final String FILE_ID = "fileId";
    public static final String FILE_IDS = "fileIds";
    public static final String START_EPOCH_MS = "startEpochMs";
    public static final String END_EPOCH_MS = "endEpochMs";
    public static final String RETRY = "retry";
    public static final String LIMIT = "limit";
    public static final String CURSOR = "cursor";
    public static final String HUB = "hub";
    public static final String WORKSPACE = "workspace";
    public static final String PROJECT = "project";
    public static final String WITHIN_LAST_MINUTES = "withinLastMinutes";
    public static final String STATUS = "status";
    public static final String OPERATION_ID = "operationId";
    public static final String RECORDING_ID = "recordingId";
    public static final String PROFILE_ID = "profileId";
    public static final String PATH = "path";

    private static final String POLL_WHY = "reports the transfer's progress, and its result once it lands";
    private static final String ANALYSE_WHY = "builds the profile every analysis tool takes from the downloaded "
            + "recording";
    private static final String SUMMARY_WHY = "the profile is already built: its summary is where an analysis starts";

    private HubCalls() {
    }

    /** A call to one of the hub tools on a session; add the rest of its arguments and a why. */
    public static McpNextTool.Call onSession(String tool, String sessionRef) {
        return McpNextTool.call(tool).with(SESSION_REF, sessionRef);
    }

    /** The poll of a transfer still running. */
    public static McpNextTool poll(String operationId) {
        return McpNextTool.call(OPERATIONS_STATUS).with(OPERATION_ID, operationId).why(POLL_WHY);
    }

    /** The analysis of a recording a download brought and nothing has analysed yet. */
    public static McpNextTool analyse(String recordingId) {
        return McpNextTool.call(RECORDINGS_ANALYZE_RECORDING).with(RECORDING_ID, recordingId).why(ANALYSE_WHY);
    }

    /** Where a recording already analysed is read from. */
    public static McpNextTool summary(String profileId) {
        return McpNextTool.call(PROFILES_SUMMARY).with(PROFILE_ID, profileId).why(SUMMARY_WHY);
    }
}
