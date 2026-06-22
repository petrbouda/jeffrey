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

export interface ClaudeCodeDetectResponse {
  aiConfigured: boolean;
  claudeCodeDetected: boolean;
}

/**
 * Checks, on startup, whether the Claude Code CLI is installed while no AI provider is configured. The
 * backend returns 204 (→ empty/null here) when there is nothing to prompt.
 */
export default class AiProviderDetectClient extends BasePlatformClient {
  constructor() {
    super('/ai');
  }

  detect(): Promise<ClaudeCodeDetectResponse | null> {
    return this.get<ClaudeCodeDetectResponse>('/claude-code-detect', undefined, {
      suppressToast: true
    }).catch(() => null);
  }
}
