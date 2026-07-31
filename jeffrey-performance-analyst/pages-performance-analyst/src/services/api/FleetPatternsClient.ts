/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import BasePlatformClient from '@shared/services/api/BasePlatformClient';
import type FleetPatterns from '@/services/api/model/FleetPatterns';

/**
 * Reads the fleet-wide pattern rollup: the grounded hotspots that recur across projects. Read-only
 * over stored claims, so it works whether or not an AI provider is currently configured.
 */
export default class FleetPatternsClient extends BasePlatformClient {
  constructor() {
    super('/fleet-patterns');
  }

  async load(limit = 10): Promise<FleetPatterns> {
    return super.get<FleetPatterns>(`?limit=${limit}`);
  }
}
