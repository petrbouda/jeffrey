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

import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { ErrorResponse, isErrorResponse, isNotFoundError } from '@/services/api/model/ErrorResponse';
import { ToastService } from '@/services/ToastService';

/**
 * Extended Axios request config with custom options
 */
declare module 'axios' {
  interface InternalAxiosRequestConfig {
    /** Set to true to suppress automatic toast notification for this request */
    suppressToast?: boolean;
  }
}

/**
 * Custom error class that wraps API errors with typed ErrorResponse
 */
export class ApiError extends Error {
  readonly errorResponse?: ErrorResponse;
  readonly statusCode: number;
  readonly isNetworkError: boolean;

  constructor(
    message: string,
    statusCode: number,
    errorResponse?: ErrorResponse,
    isNetworkError = false
  ) {
    super(message);
    this.name = 'ApiError';
    this.statusCode = statusCode;
    this.errorResponse = errorResponse;
    this.isNetworkError = isNetworkError;
  }

  /**
   * Check if this is a "not found" error
   */
  isNotFound(): boolean {
    return this.errorResponse ? isNotFoundError(this.errorResponse) : this.statusCode === 404;
  }

  /**
   * Check if this is a client error (4xx)
   */
  isClientError(): boolean {
    return this.statusCode >= 400 && this.statusCode < 500;
  }

  /**
   * Check if this is a server error (5xx)
   */
  isServerError(): boolean {
    return this.statusCode >= 500;
  }
}

/**
 * Get a user-friendly error title based on the error type
 */
function getErrorTitle(error: ApiError): string {
  if (error.isNetworkError) {
    return 'Connection Error';
  }

  if (error.errorResponse) {
    switch (error.errorResponse.code) {
      case 'WORKSPACE_NOT_FOUND':
        return 'Workspace Not Found';
      case 'PROJECT_NOT_FOUND':
        return 'Project Not Found';
      case 'RECORDING_SESSION_NOT_FOUND':
        return 'Recording Session Not Found';
      case 'RECORDING_FILE_NOT_FOUND':
        return 'Recording File Not Found';
      case 'REMOTE_JEFFREY_UNAVAILABLE':
        return 'Remote Server Unavailable';
      case 'EMPTY_RECORDING_SESSION':
        return 'Empty Recording Session';
      case 'INVALID_REQUEST':
        return 'Invalid Request';
      case 'SCHEDULER_JOB_NOT_FOUND':
        return 'Scheduler Job Not Found';
      case 'PROFILER_CONFIGURATION_ERROR':
        return 'Profiler Configuration Error';
      case 'REMOTE_OPERATION_FAILED':
        return 'Remote Operation Failed';
      case 'HEAP_DUMP_CORRUPTED':
        return 'Heap Dump Corrupted';
      case 'REPOSITORY_NOT_FOUND':
        return 'Repository Not Found';
      case 'COMPRESSION_ERROR':
        return 'Compression Error';
      case 'RESOURCE_NOT_FOUND':
        return 'Not Found';
      default:
        return 'Error';
    }
  }

  if (error.statusCode === 404) {
    return 'Not Found';
  } else if (error.statusCode === 400) {
    return 'Bad Request';
  } else if (error.statusCode === 401) {
    return 'Unauthorized';
  } else if (error.statusCode === 403) {
    return 'Forbidden';
  } else if (error.statusCode >= 500) {
    return 'Server Error';
  }

  return 'Error';
}

/**
 * Show toast notification for the error
 */
function showErrorToast(error: ApiError): void {
  const title = getErrorTitle(error);
  const message = error.errorResponse?.message || error.message;
  ToastService.error(title, message);
}

/**
 * Parse error response from data, handling Blob responses from file downloads
 */
async function parseErrorResponse(data: unknown): Promise<ErrorResponse | undefined> {
  // Blob download returned JSON error — parse it
  if (data instanceof Blob && data.type === 'application/json') {
    try {
      const text = await data.text();
      const parsed = JSON.parse(text);
      if (isErrorResponse(parsed)) {
        return parsed;
      }
    } catch {
      // Failed to parse blob as JSON, fall through
    }
  }

  if (isErrorResponse(data)) {
    return data;
  }

  return undefined;
}

/**
 * Response error interceptor
 */
async function responseErrorInterceptor(error: AxiosError): Promise<never> {
  const config = error.config as InternalAxiosRequestConfig | undefined;
  const suppressToast = config?.suppressToast ?? false;

  let apiError: ApiError;

  if (error.response) {
    // Server responded with an error status
    const statusCode = error.response.status;
    const data = error.response.data;

    const errorResponse = await parseErrorResponse(data);

    const message = errorResponse?.message ||
      (typeof data === 'string' ? data : `Request failed with status ${statusCode}`);

    apiError = new ApiError(message, statusCode, errorResponse);
  } else if (error.request) {
    // Request was made but no response received (network error)
    apiError = new ApiError(
      'Unable to connect to the server. Please check your network connection.',
      0,
      undefined,
      true
    );
  } else {
    // Something else happened
    apiError = new ApiError(error.message || 'An unexpected error occurred', 0);
  }

  // Auto-show toast unless suppressed
  if (!suppressToast) {
    showErrorToast(apiError);
  }

  // Log error to console for debugging
  console.error('API Error:', {
    url: config?.url,
    method: config?.method,
    status: apiError.statusCode,
    error: apiError.errorResponse || apiError.message
  });

  return Promise.reject(apiError);
}

/**
 * Register HTTP interceptors with Axios
 * Call this once during application initialization
 */
export function registerHttpInterceptors(): void {
  // Response interceptor for error handling
  axios.interceptors.response.use(
    // Success handler - pass through unchanged
    (response) => response,
    // Error handler
    responseErrorInterceptor
  );
}

export default {
  registerHttpInterceptors,
  ApiError
};
