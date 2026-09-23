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

import GCHeader from '@/services/api/model/GCHeader';
import GCEvent from '@/services/api/model/GCEvent';
import GCPauseDistribution from '@/services/api/model/GCPauseDistribution';
import GCEfficiency from '@/services/api/model/GCEfficiency';
import GCGenerationStats from '@/services/api/model/GCGenerationStats';
import ConcurrentEvent from '@/services/api/model/ConcurrentEvent';

export default class GCOverviewData {
  constructor(
    public header: GCHeader,
    public longestPauses: GCEvent[],
    public pauseDistribution: GCPauseDistribution,
    public efficiency: GCEfficiency,
    public generationStats: GCGenerationStats[],
    public longestConcurrentEvents: ConcurrentEvent[] | null
  ) {}
}
