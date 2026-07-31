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
import type AiPrompt from '@/services/api/model/AiPrompt';

export default class FlamegraphAiExportClient extends BasePlatformClient {
  constructor() {
    super('/recordings');
  }

  /**
   * Parses the recording's JFR file(s) (server-side, cached after first call) and returns one AI
   * prompt per profile that produced samples — CPU, wall-clock, allocation and blocking.
   *
   * `projectId` selects the project's prune threshold, which decides how much of the call tree
   * reaches the model. Without it the backend falls back to the installation default, so an unscoped
   * recording still works but ignores whatever the project was configured with.
   */
  async generate(recordingId: string, projectId?: string | null): Promise<AiPrompt[]> {
    const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : '';
    return this.post<AiPrompt[]>(`/${recordingId}/ai-flamegraph-export${query}`);
  }

  /**
   * Returns the already-cached prompts without generating anything (empty if none yet). Used to show
   * the "AI prompts ready" status on the recording row.
   */
  async peek(recordingId: string): Promise<AiPrompt[]> {
    return this.get<AiPrompt[]>(`/${recordingId}/ai-flamegraph-export`);
  }
}
