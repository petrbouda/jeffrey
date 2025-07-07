/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import GCHeader from '@/services/profile/gc/GCHeader';
import GCEvent from '@/services/profile/gc/GCEvent';
import GCPauseDistribution from '@/services/profile/gc/GCPauseDistribution';
import GCEfficiency from '@/services/profile/gc/GCEfficiency';
import GCGenerationStats from '@/services/profile/gc/GCGenerationStats';
import ConcurrentEvent from '@/services/profile/gc/ConcurrentEvent';

export default class GCOverviewData {
    constructor(
        public header: GCHeader,
        public longestPauses: GCEvent[],
        public pauseDistribution: GCPauseDistribution,
        public efficiency: GCEfficiency,
        public generationStats: GCGenerationStats[],
        public longestConcurrentEvents: ConcurrentEvent[] | null
    ) {
    }
}
