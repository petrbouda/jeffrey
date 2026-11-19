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

import axios from 'axios';
import GlobalVars from '@/services/GlobalVars';
import HttpUtils from '@shared/services/HttpUtils';
import type { RequestOptions } from '@shared/services/api/BasePlatformClient';

/**
 * Base class for profile feature API clients.
 * Provides common functionality for making HTTP requests to profile-related endpoints.
 * Uses simplified URLs: /profiles/{profileId}/{featurePath}
 */
export default abstract class BaseProfileClient {
  protected readonly baseUrl: string;

  /**
   * Creates a new profile client instance using simplified URL pattern.
   * @param profileId - The profile identifier
   * @param featurePath - The feature-specific path suffix (e.g., 'gc', 'heap-memory', 'container')
   */
  constructor(profileId: string, featurePath: string) {
    this.baseUrl = `${GlobalVars.internalUrl}/profiles/${profileId}/${featurePath}`;
  }

  private static applyOptions(
    config: Record<string, any>,
    options?: RequestOptions
  ): Record<string, any> {
    let applied = config;
    if (options?.repeatArrayParams) {
      applied = { ...applied, paramsSerializer: { indexes: null } };
    }
    if (options?.suppressToast) {
      applied = { ...applied, suppressToast: true };
    }
    return applied;
  }

  /**
   * Makes a GET request to the specified path.
   * @param path - The path relative to the base URL (should start with '/' or be empty)
   * @param params - Optional query parameters
   * @returns Promise resolving to the response data
   */
  protected get<T>(
    path: string,
    params?: Record<string, any>,
    options?: RequestOptions
  ): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    const config = params
      ? HttpUtils.JSON_ACCEPT_WITH_PARAMS(params)
      : HttpUtils.JSON_ACCEPT_HEADER;
    return axios
      .get<T>(url, BaseProfileClient.applyOptions(config, options))
      .then(HttpUtils.RETURN_DATA);
  }

  /**
   * Makes a POST request to the specified path.
   * @param path - The path relative to the base URL (should start with '/' or be empty)
   * @param body - The request body
   * @returns Promise resolving to the response data
   */
  protected post<T>(path: string, body: Record<string, any>, options?: RequestOptions): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    return axios
      .post<T>(
        url,
        body,
        BaseProfileClient.applyOptions(HttpUtils.JSON_CONTENT_TYPE_HEADER, options)
      )
      .then(HttpUtils.RETURN_DATA);
  }

  /**
   * Makes a PUT request to the specified path.
   * @param path - The path relative to the base URL (should start with '/' or be empty)
   * @param body - The request body
   * @returns Promise resolving to the response data
   */
  protected put<T>(path: string, body: Record<string, any>, options?: RequestOptions): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    return axios
      .put<T>(
        url,
        body,
        BaseProfileClient.applyOptions(HttpUtils.JSON_CONTENT_TYPE_HEADER, options)
      )
      .then(HttpUtils.RETURN_DATA);
  }

  /**
   * Makes a DELETE request to the specified path.
   * @param path - The path relative to the base URL (should start with '/' or be empty)
   * @returns Promise resolving to the response data
   */
  protected delete<T>(path: string, options?: RequestOptions): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    return axios
      .delete<T>(url, BaseProfileClient.applyOptions(HttpUtils.JSON_ACCEPT_HEADER, options))
      .then(HttpUtils.RETURN_DATA);
  }
}
