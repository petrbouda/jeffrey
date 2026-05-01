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

import BaseProfileClient from './BaseProfileClient';
import FlagsData from './model/FlagsData';

/**
 * API client for JVM flags dashboard functionality.
 */
export default class FlagsClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'flags');
  }

  /**
   * Retrieves all JVM flags grouped by origin.
   * @returns Promise resolving to flags data with grouping and change detection
   */
  getAllFlags(): Promise<FlagsData> {
    return this.get<FlagsData>('');
  }
}
