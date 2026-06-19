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
import type GuardianGuard from '@/services/api/model/GuardianGuard';
import type { GuardianGuardRequest } from '@/services/api/model/GuardianGuard';

/**
 * CRUD client for the central, editable Guardian guard definitions.
 */
export default class GuardianGuardsClient extends BasePlatformClient {
  constructor() {
    super('/guardian/guards');
  }

  list(): Promise<GuardianGuard[]> {
    return super.get<GuardianGuard[]>();
  }

  get(guardId: string): Promise<GuardianGuard> {
    return super.get<GuardianGuard>(`/${guardId}`);
  }

  create(request: GuardianGuardRequest): Promise<GuardianGuard> {
    return super.post<GuardianGuard>('', request);
  }

  update(guardId: string, request: GuardianGuardRequest): Promise<GuardianGuard> {
    return super.put<GuardianGuard>(`/${guardId}`, request);
  }

  remove(guardId: string): Promise<void> {
    return super.del<void>(`/${guardId}`);
  }
}
