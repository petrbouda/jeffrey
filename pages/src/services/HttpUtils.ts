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

    static RETURN_DATA(response: AxiosResponse): any {
        return response.data;
    }
}
