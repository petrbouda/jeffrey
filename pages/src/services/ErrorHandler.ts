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

import { ApiError } from '@/services/HttpInterceptor';
import { ErrorResponse, isErrorResponse } from '@/services/model/ErrorResponse';
import { ToastService } from '@/services/ToastService';

/**
 * Extract error message from various error types
 */
export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.errorResponse?.message || error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  if (typeof error === 'string') {
    return error;
  }

  if (isErrorResponse(error)) {
    return error.message;
  }

  return 'An unexpected error occurred';
}

/**
 * Extract ErrorResponse from various error types
 */
export function getErrorResponse(error: unknown): ErrorResponse | undefined {
  if (error instanceof ApiError) {
    return error.errorResponse;
  }

  if (isErrorResponse(error)) {
    return error;
  }

  return undefined;
}

/**
 * Check if error is a "not found" type error
 */
export function isNotFoundError(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.isNotFound();
  }

  const response = getErrorResponse(error);
  if (response) {
    return (
      response.code === 'WORKSPACE_NOT_FOUND' ||
      response.code === 'PROJECT_NOT_FOUND' ||
      response.code === 'RECORDING_SESSION_NOT_FOUND' ||
      response.code === 'RECORDING_FILE_NOT_FOUND' ||
      response.code === 'SCHEDULER_JOB_NOT_FOUND'
    );
  }

  return false;
}

/**
 * Check if error is a network/connection error
 */
export function isNetworkError(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.isNetworkError;
  }
  return false;
}

/**
 * Check if error is a remote Jeffrey unavailable error
 */
export function isRemoteUnavailableError(error: unknown): boolean {
  const response = getErrorResponse(error);
  return response?.code === 'REMOTE_JEFFREY_UNAVAILABLE';
}

/**
 * Handle API error with optional custom context
 * Shows toast if not already shown by interceptor
 *
 * @param error The error to handle
 * @param context Optional context string to prepend to error message
 * @param showToast Whether to show a toast (default: false, since interceptor handles it)
 */
export function handleApiError(
  error: unknown,
  context?: string,
  showToast = false
): void {
  const message = getErrorMessage(error);
  const fullMessage = context ? `${context}: ${message}` : message;

  // Log to console
  console.error('API Error:', fullMessage, error);

  // Show toast if requested (usually not needed since interceptor handles it)
  if (showToast) {
    ToastService.error('Error', fullMessage);
  }
}

/**
 * Execute an async operation with error handling
 * Returns the result on success, or undefined on error
 *
 * @param operation The async operation to execute
 * @param context Optional context for error messages
 * @returns The result of the operation, or undefined if it failed
 */
export async function withErrorHandling<T>(
  operation: () => Promise<T>,
  context?: string
): Promise<T | undefined> {
  try {
    return await operation();
  } catch (error) {
    handleApiError(error, context);
    return undefined;
  }
}

export default {
  getErrorMessage,
  getErrorResponse,
  isNotFoundError,
  isNetworkError,
  isRemoteUnavailableError,
  handleApiError,
  withErrorHandling
};
