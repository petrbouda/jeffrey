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

import ThreadCommon from './ThreadCommon';
import ThreadRowData from '@/services/api/model/ThreadRowData';

/**
 * One page of a profile's threads.
 *
 * A recording can hold thousands of threads, so the timeline asks for the busiest ones first and
 * comes back for more. `matchedCount` is what "showing 50 of N" counts — it narrows with the filter,
 * while `totalCount` stays the size of the recording.
 */
export default class ThreadResponse {
  constructor(
    public common: ThreadCommon,
    public rows: ThreadRowData[],
    public offset: number,
    public matchedCount: number,
    public totalCount: number
  ) {}
}

export type ThreadSort = 'EVENT_COUNT' | 'LIFESPAN' | 'NAME';
