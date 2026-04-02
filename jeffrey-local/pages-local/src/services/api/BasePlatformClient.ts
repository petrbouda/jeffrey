/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import axios from 'axios';
import GlobalVars from '@/services/GlobalVars';
import HttpUtils from '@/services/HttpUtils';

/**
 * Base class for platform API clients that operate on workspace/project-scoped resources.
 * Provides common HTTP methods with standard JSON headers.
 */
export default abstract class BasePlatformClient {
  protected readonly baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = GlobalVars.internalUrl + baseUrl;
  }

  protected get<T>(path: string = '', params?: Record<string, any>): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    const config = params
      ? HttpUtils.JSON_ACCEPT_WITH_PARAMS(params)
      : HttpUtils.JSON_ACCEPT_HEADER;
    return axios.get<T>(url, config).then(HttpUtils.RETURN_DATA);
  }

  protected post<T>(path: string = '', body?: any): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    return axios.post<T>(url, body, HttpUtils.JSON_HEADERS).then(HttpUtils.RETURN_DATA);
  }

  protected put<T>(path: string = '', body?: any): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    return axios.put<T>(url, body, HttpUtils.JSON_HEADERS).then(HttpUtils.RETURN_DATA);
  }

  protected del<T>(path: string = ''): Promise<T> {
    const url = path ? `${this.baseUrl}${path}` : this.baseUrl;
    return axios.delete<T>(url, HttpUtils.JSON_ACCEPT_HEADER).then(HttpUtils.RETURN_DATA);
  }
}
