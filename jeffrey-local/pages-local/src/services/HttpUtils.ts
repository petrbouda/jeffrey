/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import axios, {AxiosResponse} from "axios";

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
