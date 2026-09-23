/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import PoolConfiguration from '@/services/api/model/PoolConfiguration.ts';
import PoolStatistics from '@/services/api/model/PoolStatistics.ts';
import PoolEventStatistics from '@/services/api/model/PoolEventStatistics.ts';

export default class PoolData {
  constructor(
    public poolName: string,
    public configuration: PoolConfiguration,
    public statistics: PoolStatistics,
    public eventStatistics: PoolEventStatistics[]
  ) {}
}
