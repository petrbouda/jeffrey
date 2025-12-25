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

public enum ErrorCode {
    WORKSPACE_NOT_FOUND,
    PROJECT_NOT_FOUND,
    RECORDING_SESSION_NOT_FOUND,
    RECORDING_FILE_NOT_FOUND,
    UNKNOWN_ERROR_RESPONSE,
    REMOTE_JEFFREY_UNAVAILABLE,
    EMPTY_RECORDING_SESSION,
    COMPRESSION_ERROR,

    // Validation errors
    INVALID_REQUEST,

    // Scheduler errors
    SCHEDULER_JOB_NOT_FOUND,

    // Profiler errors
    PROFILER_CONFIGURATION_ERROR,

    // Remote operation errors
    REMOTE_OPERATION_FAILED;

    public boolean isNotFound() {
        return this == WORKSPACE_NOT_FOUND
               || this == PROJECT_NOT_FOUND
               || this == RECORDING_SESSION_NOT_FOUND
               || this == RECORDING_FILE_NOT_FOUND
               || this == SCHEDULER_JOB_NOT_FOUND;
    }
}
