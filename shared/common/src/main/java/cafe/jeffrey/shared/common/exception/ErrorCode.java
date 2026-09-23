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

import java.util.EnumSet;
import java.util.Set;

public enum ErrorCode {
    WORKSPACE_NOT_FOUND,
    PROJECT_NOT_FOUND,
    PROFILE_NOT_FOUND,
    RECORDING_NOT_FOUND,
    RECORDING_SESSION_NOT_FOUND,
    RECORDING_FILE_NOT_FOUND,
    REPOSITORY_NOT_FOUND,
    UNKNOWN_ERROR_RESPONSE,
    HUB_UNAVAILABLE,
    EMPTY_RECORDING_SESSION,

    // Validation errors
    INVALID_REQUEST,

    // Scheduler errors
    SCHEDULER_JOB_NOT_FOUND,

    // Profiler errors
    PROFILER_CONFIGURATION_ERROR,

    // Remote operation errors
    REMOTE_OPERATION_FAILED,

    // Heap dump errors
    HEAP_DUMP_CORRUPTED,
    HEAP_DUMP_NEEDS_SANITIZATION,

    // Generic not-found error for Jersey WebApplicationExceptions
    RESOURCE_NOT_FOUND;

    private static final Set<ErrorCode> NOT_FOUND_CODES = EnumSet.of(
            WORKSPACE_NOT_FOUND,
            PROJECT_NOT_FOUND,
            PROFILE_NOT_FOUND,
            RECORDING_NOT_FOUND,
            RECORDING_SESSION_NOT_FOUND,
            RECORDING_FILE_NOT_FOUND,
            REPOSITORY_NOT_FOUND,
            SCHEDULER_JOB_NOT_FOUND,
            RESOURCE_NOT_FOUND);

    public boolean isNotFound() {
        return NOT_FOUND_CODES.contains(this);
    }
}
