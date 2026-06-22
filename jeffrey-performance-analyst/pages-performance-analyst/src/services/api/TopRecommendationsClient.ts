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
import type TopSeverityOverview from '@/services/api/model/TopSeverityOverview';

/**
 * Reads the global Overview's "Highest Impact" payload: the most severe recommendations across all
 * projects plus per-severity counts. Always available (reads stored rows even when AI is off).
 */
export default class TopRecommendationsClient extends BasePlatformClient {
  constructor() {
    super('/recommendations');
  }

  loadTopSeverity(limit?: number): Promise<TopSeverityOverview> {
    return super.get<TopSeverityOverview>('/top-severity', limit ? { limit } : undefined);
  }
}
