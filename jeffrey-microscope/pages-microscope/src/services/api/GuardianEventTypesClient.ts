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
import type GuardianEventTypeOption from '@/services/api/model/GuardianEventTypeOption';

/**
 * Read-only client for the catalog of stack-trace–carrying event types offered by the guard editor.
 */
export default class GuardianEventTypesClient extends BasePlatformClient {
  constructor() {
    super('/guardian/event-types');
  }

  list(): Promise<GuardianEventTypeOption[]> {
    return super.get<GuardianEventTypeOption[]>();
  }
}
