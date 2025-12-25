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

import {AxiosResponse} from "axios";
import {decode} from "@msgpack/msgpack";

export default class HttpUtils {
    static MSGPACK_MEDIA_TYPE = 'application/msgpack';

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

    /**
     * Request configuration for MessagePack responses.
     * Uses MessagePack for compact binary serialization (30-50% smaller than JSON).
     */
    static MSGPACK_HEADERS = {
        headers: {
            'Content-Type': 'application/json',
            Accept: HttpUtils.MSGPACK_MEDIA_TYPE
        },
        responseType: 'arraybuffer' as const
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

    /**
     * Decodes MessagePack binary response to typed object.
     * @param response Axios response with arraybuffer data
     * @returns Decoded object
     */
    static DECODE_MSGPACK<T>(response: AxiosResponse<ArrayBuffer>): T {
        return decode(new Uint8Array(response.data)) as T;
    }
}
