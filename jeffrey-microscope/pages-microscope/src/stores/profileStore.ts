/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
