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

import pako from "pako";

export default class CompressionUtils {

    static decodeAndDecompress(content) {
        const decoded = window.atob(content)

        // Convert binary string to character-number array
        const charData = decoded.split('').map(function (x) {
            return x.charCodeAt(0);
        });

        // Turn number array into byte-array
        const binData = new Uint8Array(charData);

        return pako.ungzip(binData, { to: 'string' });
    }
}
