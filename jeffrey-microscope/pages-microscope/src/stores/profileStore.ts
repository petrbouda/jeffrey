/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import { ref, computed } from 'vue';
import Profile from '@/services/api/model/Profile';

/**
 * Profile with workspace and project context.
 * This is what we receive from the new /profiles/{profileId} endpoint.
 */
/** Where a recording starts on the wall clock, and how long it lasted. */
export interface RecordingWindow {
  startEpochMillis: number;
  durationMillis: number;
}

export interface ProfileWithContext extends Profile {
  hubId: string;
  workspaceId: string;
  projectId: string;
}

/**
 * Global profile store for managing current profile context.
 * When using simplified URLs (/profiles/{profileId}/...), this store
 * provides the workspace and project context that was previously in the URL.
 */

// Reactive state
const currentProfile = ref<ProfileWithContext | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);

// Computed properties for easy access
const profileId = computed(() => currentProfile.value?.id ?? '');
const projectId = computed(() => currentProfile.value?.projectId ?? '');
const workspaceId = computed(() => currentProfile.value?.workspaceId ?? '');
const hubId = computed(() => currentProfile.value?.hubId ?? '');
const profileName = computed(() => currentProfile.value?.name ?? '');
const isLoaded = computed(() => currentProfile.value !== null);

/**
 * The recording's own timeline: where its zero is, and how long it ran.
 * <p>
 * What every relative-time view needs — a chart drawn over the recording rather than over whatever
 * subset of it the page happens to hold. Null until the profile is loaded, or when the recording
 * never reported its bounds.
 */
const recordingWindow = computed<RecordingWindow | null>(() => {
  const profile = currentProfile.value;
  const startEpochMillis = profile?.profilingStartedAt;
  const endEpochMillis = profile?.profilingFinishedAt;
  if (startEpochMillis == null || endEpochMillis == null || endEpochMillis <= startEpochMillis) {
    return null;
  }
  return { startEpochMillis, durationMillis: endEpochMillis - startEpochMillis };
});

/**
 * Sets the current profile with its workspace and project context.
 */
const setProfile = (profile: ProfileWithContext) => {
  currentProfile.value = profile;
  error.value = null;
};

/**
 * Clears the current profile context.
 */
const clearProfile = () => {
  currentProfile.value = null;
  error.value = null;
};

/**
 * Sets the loading state.
 */
const setLoading = (isLoading: boolean) => {
  loading.value = isLoading;
};

/**
 * Sets an error message.
 */
const setError = (errorMessage: string | null) => {
  error.value = errorMessage;
};

/**
 * Global profile store.
 * Use this in components that need to access profile context
 * when using simplified URLs.
 */
export const profileStore = {
  // Reactive state
  currentProfile,
  loading,
  error,

  // Computed properties
  profileId,
  projectId,
  workspaceId,
  hubId,
  profileName,
  isLoaded,
  recordingWindow,

  // Actions
  setProfile,
  clearProfile,
  setLoading,
  setError
};
