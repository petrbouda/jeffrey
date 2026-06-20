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

import axios from 'axios';
import BasePlatformClient from '@shared/services/api/BasePlatformClient';

export default class FlamegraphAiExportClient extends BasePlatformClient {
  constructor() {
    super('/recordings');
  }

  /**
   * Parses the recording's JFR file(s) in memory and builds the AI flamegraph prompt(s) for the
   * jdk.ExecutionSample and profiler.WallClockSample events. The backend prints them to STDOUT and
   * returns the combined markdown.
   */
  async generate(recordingId: string): Promise<string> {
    const url = `${this.baseUrl}/${recordingId}/ai-flamegraph-export`;
    const response = await axios.post<string>(url, null, { headers: { Accept: 'text/markdown' } });
    return response.data;
  }
}
