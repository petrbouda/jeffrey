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

import axios from 'axios';
import GlobalVars from '@/services/GlobalVars';
import HttpUtils from '@shared/services/HttpUtils';

export interface RequestOptions {
  suppressToast?: boolean;
  /**
   * Serialise an array parameter as a repeated bare key — `?where=a&where=b`.
   *
   * Axios appends `[]` to a repeated key by default, which Spring does not bind to a `List<String>`
   * request parameter: it looks for the bare name. Needed wherever a filter is several conditions of
   * the same kind rather than one value.
   */
  repeatArrayParams?: boolean;
}

/**
 * Base class for platform API clients that operate on workspace/project-scoped resources.
 * Provides common HTTP methods with standard JSON headers.
 */
export default abstract class BasePlatformClient {
  protected readonly baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = GlobalVars.internalUrl + baseUrl;
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

  protected get<T>(
    path: string = '',
    params?: Record<string, any>,
    options?: RequestOptions
  ): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    const config = params
      ? HttpUtils.JSON_ACCEPT_WITH_PARAMS(params)
      : HttpUtils.JSON_ACCEPT_HEADER;
    return axios
      .get<T>(url, BasePlatformClient.applyOptions(config, options))
      .then(HttpUtils.RETURN_DATA);
  }

  protected post<T>(path: string = '', body?: any, options?: RequestOptions): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    return axios
      .post<T>(url, body, BasePlatformClient.applyOptions(HttpUtils.JSON_HEADERS, options))
      .then(HttpUtils.RETURN_DATA);
  }

  protected put<T>(path: string = '', body?: any, options?: RequestOptions): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    return axios
      .put<T>(url, body, BasePlatformClient.applyOptions(HttpUtils.JSON_HEADERS, options))
      .then(HttpUtils.RETURN_DATA);
  }

  protected del<T>(path: string = '', options?: RequestOptions): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    return axios
      .delete<T>(url, BasePlatformClient.applyOptions(HttpUtils.JSON_ACCEPT_HEADER, options))
      .then(HttpUtils.RETURN_DATA);
  }
}
