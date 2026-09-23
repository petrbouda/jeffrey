/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import ideConfigStore from '@/stores/ideConfigStore';
import IdeClient from '@/services/api/IdeClient';
import type { IdeFailureReason } from '@/services/api/IdeClient';
import ideProfileTargetStore from '@/stores/ideProfileTargetStore';
import { ToastService } from '@shared/services/ToastService';

/**
 * Drives "Open in IDE" jumps. The backend tries the cached window directly (no discovery); only when
 * a jump fails because the target is missing/offline do we offer to (re-)select a window — which is
 * the single place port discovery runs from a jump. A reachable-but-unresolved symbol is reported as
 * a plain message.
 */
export default class IdeJumpService {
  private static readonly FAILURE_TITLE = 'IDE jump failed — is the IDE plugin running?';
  private static readonly RESELECT_REASONS: ReadonlySet<IdeFailureReason> = new Set([
    'NO_TARGET',
    'UNREACHABLE'
  ]);

  static async openInIde(
    profileId: string,
    fqn: string,
    method: string,
    line: number
  ): Promise<void> {
    if (!ideConfigStore.isEnabled()) {
      return;
    }

    const client = new IdeClient();
    try {
      let response = await client.open(profileId, fqn, method, line);
      if (response.success) {
        return;
      }

      if (IdeJumpService.canReselect(response.reason)) {
        const picked = await ideProfileTargetStore.selectOrChange(profileId);
        if (!picked) {
          // Cancelled, or no running IDE (the store already showed a toast).
          return;
        }
        response = await client.open(profileId, fqn, method, line);
        if (response.success) {
          return;
        }
      }

      ToastService.warn(IdeJumpService.FAILURE_TITLE, response.message ?? '');
    } catch (err) {
      const detail = err instanceof Error ? err.message : String(err);
      ToastService.warn(IdeJumpService.FAILURE_TITLE, detail);
    }
  }

  private static canReselect(reason: IdeFailureReason): boolean {
    return (
      ideProfileTargetStore.status.value.selectable && IdeJumpService.RESELECT_REASONS.has(reason)
    );
  }
}
