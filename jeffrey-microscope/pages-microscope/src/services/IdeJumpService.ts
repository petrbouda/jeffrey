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

import ideConfigStore from '@/stores/ideConfigStore';
import IdeClient from '@/services/api/IdeClient';
import IdeTargetService from '@/services/IdeTargetService';
import { ToastService } from '@/services/ToastService';

export default class IdeJumpService {
  private static readonly FAILURE_TITLE = 'IDE jump failed — is the IDE plugin running?';
  private static readonly NO_IDE_MESSAGE = 'No running IDE was found. Open your project in IntelliJ with the Jeffrey plugin installed.';

  static async openInIde(profileId: string, fqn: string, method: string, line: number): Promise<void> {
    if (!ideConfigStore.isEnabled()) {
      return;
    }

    try {
      const { target, reason } = await IdeTargetService.resolve(profileId, fqn);
      if (!target) {
        if (reason === 'no-ide') {
          ToastService.warn(IdeJumpService.FAILURE_TITLE, IdeJumpService.NO_IDE_MESSAGE);
        }
        // 'cancelled' — user dismissed the picker; stay silent.
        return;
      }

      const response = await new IdeClient().open(profileId, fqn, method, line);
      if (!response.success) {
        ToastService.warn(IdeJumpService.FAILURE_TITLE, response.message ?? '');
      }
    } catch (err) {
      const detail = err instanceof Error ? err.message : String(err);
      ToastService.warn(IdeJumpService.FAILURE_TITLE, detail);
    }
  }
}
