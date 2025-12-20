/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.exception;

import java.net.URI;

public abstract class Exceptions {
    public static JeffreyException fromErrorResponse(ErrorResponse errorResponse) {
        if (errorResponse == null) {
            throw internal("Error response cannot be null");
        }

        return switch (errorResponse.type()) {
            case CLIENT -> new JeffreyClientException(errorResponse.code(), errorResponse.message());
            case INTERNAL -> new JeffreyInternalException(errorResponse.code(), errorResponse.message());
        };
    }

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

    public static JeffreyClientException recordingSessionNotFound(String sessionId) {
        return new JeffreyClientException(
                ErrorCode.RECORDING_SESSION_NOT_FOUND, "Recording session not found: %s".formatted(sessionId));
    }

    public static JeffreyClientException recordingFileNotFound(String fileId) {
        return new JeffreyClientException(
                ErrorCode.RECORDING_SESSION_NOT_FOUND, "Recording file not found: %s".formatted(fileId));
    }

    public static JeffreyClientException emptyRecordingSession(String sessionId) {
        return new JeffreyClientException(
                ErrorCode.EMPTY_RECORDING_SESSION, "Recording session is empty: %s".formatted(sessionId));
    }

    public static RemoteJeffreyUnavailableException remoteJeffreyUnavailable(URI uri, Throwable cause) {
        return new RemoteJeffreyUnavailableException(uri, cause);
    }

    public static JeffreyClientException invalidRequest(String message) {
        return new JeffreyClientException(ErrorCode.INVALID_REQUEST, message);
    }

    public static JeffreyClientException schedulerJobNotFound(String jobId) {
        return new JeffreyClientException(
                ErrorCode.SCHEDULER_JOB_NOT_FOUND, "Scheduler job not found: %s".formatted(jobId));
    }

    public static JeffreyClientException profilerConfigurationError(String message) {
        return new JeffreyClientException(ErrorCode.PROFILER_CONFIGURATION_ERROR, message);
    }

    public static JeffreyInternalException remoteOperationFailed(String operation, String remoteUrl, String detail) {
        return new JeffreyInternalException(
                ErrorCode.REMOTE_OPERATION_FAILED,
                "Remote operation '%s' failed for %s: %s".formatted(operation, remoteUrl, detail));
    }

}
