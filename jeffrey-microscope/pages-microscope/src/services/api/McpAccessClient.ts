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

/**
 * What the Settings page shows about the external MCP server. The URL and both snippets are built
 * server-side from the request, so they name the address this browser actually reached Jeffrey on
 * rather than a hardcoded localhost that would be wrong behind a proxy or on a non-default port.
 */
export interface McpAccessStatus {
  enabled: boolean;
  ingestEnabled: boolean;
  hubsEnabled: boolean;
  url: string;
  claudeMcpAddCommand: string;
  mcpJsonSnippet: string;
  codexMcpAddCommand: string;
  codexConfigTomlSnippet: string;
}

export default class McpAccessClient extends BasePlatformClient {
  constructor() {
    super('/mcp/access');
  }

  fetchStatus(): Promise<McpAccessStatus> {
    return super.get<McpAccessStatus>('/status');
  }
}
