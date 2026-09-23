/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import { ref } from 'vue';
import ProfileInfo from '@/services/api/model/ProfileInfo';

const STORAGE_KEY = 'secondary-profile';

/**
 * What sessionStorage holds: the chosen baseline plus the primary profile it was chosen for.
 * The selection outlives the page that made it, so without the owner there is no way to tell a
 * deliberate baseline from a leftover of the profile the user looked at before.
 */
interface StoredSecondaryProfile {
  primaryProfileId: string;
  profile: ProfileInfo;
}

export default class SecondaryProfileService {
  static profile = ref<ProfileInfo | null>(null);
  static PROFILE_CHANGED = 'secondary-profile-changed';

  static {
    const profile = this.get();
    if (profile) {
      SecondaryProfileService.profile.value = profile;
    }
  }

  static equals(id: string) {
    const profile = SecondaryProfileService.get();
    return profile != null && profile.id === id;
  }

  static update(profile: ProfileInfo, primaryProfileId: string) {
    const stored: StoredSecondaryProfile = { primaryProfileId, profile };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
    SecondaryProfileService.profile.value = profile;
    // Emit an event that the profile has changed
    const event = new CustomEvent(this.PROFILE_CHANGED, { detail: profile });
    window.dispatchEvent(event);
  }

  static remove() {
    sessionStorage.removeItem(STORAGE_KEY);
    SecondaryProfileService.profile.value = null;
    // Emit an event that the profile has been removed
    const event = new CustomEvent(this.PROFILE_CHANGED, { detail: null });
    window.dispatchEvent(event);
  }

  /**
   * Keeps the stored baseline only while the primary profile it was picked for is the one being
   * opened, and drops it otherwise. Opening another profile therefore starts with no comparison
   * rather than silently inheriting the previous one — a heap-dump baseline left over on a JFR
   * profile is meaningless, and the differential pages would happily use it.
   */
  static retainFor(primaryProfileId: string): ProfileInfo | null {
    const stored = SecondaryProfileService.read();
    if (stored == null) {
      return null;
    }
    if (stored.primaryProfileId !== primaryProfileId) {
      SecondaryProfileService.remove();
      return null;
    }
    return stored.profile;
  }

  static get(): ProfileInfo | null {
    return SecondaryProfileService.read()?.profile ?? null;
  }

  private static read(): StoredSecondaryProfile | null {
    const item = sessionStorage.getItem(STORAGE_KEY);
    if (item == null) {
      return null;
    }
    const stored = JSON.parse(item) as StoredSecondaryProfile;
    // A value written before the owner was recorded carries no profile under the new shape;
    // treat it as absent rather than handing back an undefined baseline.
    return stored?.profile != null ? stored : null;
  }

  static id(): string | null {
    const profile = SecondaryProfileService.get();
    if (profile != null) {
      return profile.id;
    } else {
      return null;
    }
  }

  static projectId(): string | null {
    const profile = SecondaryProfileService.get();
    if (profile != null) {
      return profile.projectId;
    } else {
      return null;
    }
  }

  static name(): string | null {
    const profile = SecondaryProfileService.get();
    if (profile != null) {
      return profile.name.replace('.jfr', '');
    } else {
      return null;
    }
  }
}
