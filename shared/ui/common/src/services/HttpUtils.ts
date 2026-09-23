/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import axios, { AxiosResponse } from 'axios';

export default class HttpUtils {
  static JSON_HEADERS = {
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    }
  };

  static JSON_ACCEPT_HEADER = {
    headers: {
      Accept: 'application/json'
    }
  };

  /**
   * Returns Axios config with JSON Accept header and URL params.
   * Use this instead of creating inline {headers: {Accept: 'application/json'}, params: {...}}.
   */
  static JSON_ACCEPT_WITH_PARAMS(params: Record<string, any>) {
    return {
      headers: { Accept: 'application/json' },
      params
    };
  }

  static MULTIPART_FORM_DATA_HEADER = {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  };

  static JSON_CONTENT_TYPE_HEADER = {
    headers: {
      'Content-Type': 'application/json'
    }
  };

  static PROTOBUF_MEDIA_TYPE = 'application/x-protobuf';

  /**
   * Request configuration for Protocol Buffers responses.
   * Most efficient format - 50-60% smaller than JSON with string deduplication.
   */
  static PROTOBUF_HEADERS = {
    headers: {
      'Content-Type': 'application/json',
      Accept: HttpUtils.PROTOBUF_MEDIA_TYPE
    },
    responseType: 'arraybuffer' as const
  };

  static RETURN_DATA(response: AxiosResponse): any {
    return response.data;
  }

  static async downloadFile(url: string, fallbackFilename: string): Promise<void> {
    const response = await axios.get(url, {
      responseType: 'blob'
    });

    const contentDisposition = response.headers['content-disposition'];
    let filename = fallbackFilename;
    if (contentDisposition) {
      const match = contentDisposition.match(/filename="?([^"]+)"?/);
      if (match) {
        filename = match[1];
      }
    }

    const blob = new Blob([response.data]);
    const blobUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(blobUrl);
  }
}
