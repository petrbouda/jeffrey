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
  | 'RECORDING_SESSION_NOT_FOUND'
  | 'RECORDING_FILE_NOT_FOUND'
  | 'UNKNOWN_ERROR_RESPONSE'
  | 'REMOTE_JEFFREY_UNAVAILABLE'
  | 'EMPTY_RECORDING_SESSION'
  | 'INVALID_REQUEST'
  | 'SCHEDULER_JOB_NOT_FOUND'
  | 'PROFILER_CONFIGURATION_ERROR'
  | 'REMOTE_OPERATION_FAILED'
  | 'HEAP_DUMP_CORRUPTED';

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
    error.code === 'SCHEDULER_JOB_NOT_FOUND'
  );
}

export default ErrorResponse;
