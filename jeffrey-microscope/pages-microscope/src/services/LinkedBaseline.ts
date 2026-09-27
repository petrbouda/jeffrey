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

import SecondaryProfileService from '@/services/SecondaryProfileService';
import type { ProfileWithContext } from '@/stores/profileStore';

/**
 * Adopts the baseline a URL named (`?baseline=<profileId>`), so a link can open a comparison and not
 * merely a profile. It is stored the way the picker stores one, overriding whatever the session held,
 * so everything downstream reads a single source.
 * <p>
 * A baseline that cannot be loaded leaves no baseline at all: restoring what the session was holding
 * would render a full comparison against a file the link did not name, the one case where a reader
 * cannot tell they are looking at the wrong pair. The differential pages then show their own
 * no-baseline state.
 *
 * @param load reads a profile by id
 * @return whether the linked baseline is now the secondary profile
 */
export async function adoptLinkedBaseline(
  baselineId: string,
  primaryProfileId: string,
  load: (profileId: string) => Promise<ProfileWithContext>
): Promise<boolean> {
  try {
    const linked = await load(baselineId);
    SecondaryProfileService.update(
      {
        id: linked.id,
        projectId: linked.projectId,
        name: linked.name,
        createdAt: linked.createdAt,
        profilingStartedAt: linked.profilingStartedAt ?? null,
        profilingFinishedAt: linked.profilingFinishedAt ?? null,
        enabled: linked.enabled
      },
      primaryProfileId
    );
    return true;
  } catch (error) {
    console.error('Failed to load the baseline profile named by the URL:', error);
    SecondaryProfileService.remove();
    return false;
  }
}
