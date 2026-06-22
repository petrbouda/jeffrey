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
import type AiCapabilities from '@/services/api/model/AiCapabilities';

/**
 * Reads the deployment's AI capabilities (always available, regardless of whether an AI provider is
 * configured), so the UI can gate AI-backed actions.
 */
export default class AiCapabilitiesClient extends BasePlatformClient {
  constructor() {
    super('/ai');
  }

  load(): Promise<AiCapabilities> {
    return super.get<AiCapabilities>('/capabilities');
  }
}
