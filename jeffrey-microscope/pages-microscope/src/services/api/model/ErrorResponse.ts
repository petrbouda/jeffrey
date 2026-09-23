/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

/**
 * Error type matching the backend ErrorType enum
 */
export type ErrorType = 'CLIENT' | 'INTERNAL';

/**
 * Error code matching the backend ErrorCode enum
 */
export type ErrorCode =
  | 'WORKSPACE_NOT_FOUND'
  | 'PROJECT_NOT_FOUND'
  | 'PROFILE_NOT_FOUND'
  | 'RECORDING_NOT_FOUND'
  | 'RECORDING_SESSION_NOT_FOUND'
  | 'RECORDING_FILE_NOT_FOUND'
  | 'REPOSITORY_NOT_FOUND'
  | 'UNKNOWN_ERROR_RESPONSE'
  | 'HUB_UNAVAILABLE'
  | 'EMPTY_RECORDING_SESSION'
  | 'INVALID_REQUEST'
  | 'SCHEDULER_JOB_NOT_FOUND'
  | 'PROFILER_CONFIGURATION_ERROR'
  | 'REMOTE_OPERATION_FAILED'
  | 'HEAP_DUMP_CORRUPTED'
  | 'HEAP_DUMP_NEEDS_SANITIZATION'
  | 'RESOURCE_NOT_FOUND';

/**
 * Error response structure matching the backend ErrorResponse record
 */
export interface ErrorResponse {
  type: ErrorType;
  code: ErrorCode;
  message: string;
}

/**
 * Check if the given object is an ErrorResponse
 */
export function isErrorResponse(obj: unknown): obj is ErrorResponse {
  if (typeof obj !== 'object' || obj === null) {
    return false;
  }
  const response = obj as Record<string, unknown>;
  return (
    (response.type === 'CLIENT' || response.type === 'INTERNAL') &&
    typeof response.code === 'string' &&
    typeof response.message === 'string'
  );
}

/**
 * Check if the error is a "not found" type error
 */
export function isNotFoundError(error: ErrorResponse): boolean {
  return (
    error.code === 'WORKSPACE_NOT_FOUND' ||
    error.code === 'PROJECT_NOT_FOUND' ||
    error.code === 'RECORDING_SESSION_NOT_FOUND' ||
    error.code === 'RECORDING_FILE_NOT_FOUND' ||
    error.code === 'REPOSITORY_NOT_FOUND' ||
    error.code === 'SCHEDULER_JOB_NOT_FOUND' ||
    error.code === 'RESOURCE_NOT_FOUND'
  );
}

export default ErrorResponse;
