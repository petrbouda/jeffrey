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

import JITCompilerType from "@/services/compilation/model/JITCompilerType.ts";

export default class JITLongCompilation {
    constructor(
        public compileId: number,
        public compiler: JITCompilerType,
        public method: string,
        public compileLevel: number,
        public succeeded: boolean,
        public isOsr: boolean,
        public codeSize: number,
        public inlinedBytes: number,
        public arenaBytes: number,
        public timeSpent: number) {
    }
}
