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
