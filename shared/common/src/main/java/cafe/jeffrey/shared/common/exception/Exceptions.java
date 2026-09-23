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

package cafe.jeffrey.shared.common.exception;

public abstract class Exceptions {
    public static JeffreyInternalException internal(String message) {
        return new JeffreyInternalException(ErrorCode.UNKNOWN_ERROR_RESPONSE, message);
    }

    public static JeffreyInternalException internal(String message, Exception ex) {
        return new JeffreyInternalException(ErrorCode.UNKNOWN_ERROR_RESPONSE, message, ex);
    }

    public static JeffreyClientException workspaceNotFound(String workspaceId) {
        return new JeffreyClientException(
                ErrorCode.WORKSPACE_NOT_FOUND, "Workspace not found: %s".formatted(workspaceId));
    }

    public static JeffreyClientException projectNotFound(String projectId) {
        return new JeffreyClientException(
                ErrorCode.PROJECT_NOT_FOUND, "Project not found: %s".formatted(projectId));
    }

    public static JeffreyClientException profileNotFound(String profileId) {
        return new JeffreyClientException(
                ErrorCode.PROFILE_NOT_FOUND, "Profile not found: %s".formatted(profileId));
    }

    public static JeffreyClientException recordingNotFound(String recordingId) {
        return new JeffreyClientException(
                ErrorCode.RECORDING_NOT_FOUND, "Recording not found: %s".formatted(recordingId));
    }

    public static JeffreyClientException recordingSessionNotFound(String sessionId) {
        return new JeffreyClientException(
                ErrorCode.RECORDING_SESSION_NOT_FOUND, "Recording session not found: %s".formatted(sessionId));
    }

    public static JeffreyClientException emptyRecordingSession(String sessionId) {
        return new JeffreyClientException(
                ErrorCode.EMPTY_RECORDING_SESSION,
                "No finished recording files found in session: %s. The session may still be actively recording.".formatted(sessionId));
    }

    /**
     * A 404 for a resource that has no error code of its own — a trace, a span, anything addressed
     * by an id inside a profile rather than by a top-level entity id.
     */
    public static JeffreyClientException resourceNotFound(String message) {
        return new JeffreyClientException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public static JeffreyClientException invalidRequest(String message) {
        return new JeffreyClientException(ErrorCode.INVALID_REQUEST, message);
    }
}
