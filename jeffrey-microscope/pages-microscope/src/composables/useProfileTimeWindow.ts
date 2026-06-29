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

import { computed, type ComputedRef, type Ref, ref } from 'vue';
import type { LocationQuery, Router } from 'vue-router';
import { profileStore } from '@/stores/profileStore';

/**
 * A selected slice of the recording, expressed in milliseconds elapsed from the
 * beginning of the recording (the same zero point the backend uses for relative
 * time ranges). `null` anywhere a TimeWindow is expected means "the whole recording".
 */
export interface TimeWindow {
  from: number;
  to: number;
}

const QUERY_PARAM_FROM = 'from';
const QUERY_PARAM_TO = 'to';

/**
 * Shared, app-level selected window. A single window is the source of truth for
 * the whole profile: the Recording Overview navigator writes it, every other
 * profile view reads it. Module-level state so it survives feature navigation.
 */
const activeWindow = ref<TimeWindow | null>(null);

/**
 * The profile the current window belongs to. When the user opens a different
 * profile, a leftover window from the previous one is meaningless, so it is
 * dropped (see {@link useProfileTimeWindow}).
 */
const windowProfileId = ref<string | null>(null);

const recordingDurationMillis: ComputedRef<number> = computed(
  () => profileStore.currentProfile.value?.durationInMillis ?? 0
);

const isWindowed: ComputedRef<boolean> = computed(() => activeWindow.value !== null);

/**
 * Fraction of the whole recording covered by the active window (1 when the whole
 * recording is selected). Used by the header to show "X% of recording".
 */
const windowFraction: ComputedRef<number> = computed(() => {
  const duration = recordingDurationMillis.value;
  if (duration <= 0 || activeWindow.value === null) {
    return 1;
  }
  return (activeWindow.value.to - activeWindow.value.from) / duration;
});

/**
 * The effective range to query: the active window when one is selected, otherwise
 * the whole recording [0, duration].
 */
const resolvedWindow: ComputedRef<TimeWindow> = computed(() => {
  if (activeWindow.value !== null) {
    return activeWindow.value;
  }
  return { from: 0, to: recordingDurationMillis.value };
});

function clamp(from: number, to: number): TimeWindow {
  const duration = recordingDurationMillis.value;
  const upperBound = duration > 0 ? duration : Math.max(from, to);
  const start = Math.max(0, Math.min(from, to));
  const end = Math.min(upperBound, Math.max(from, to));
  return { from: start, to: end > start ? end : upperBound };
}

function setWindow(from: number, to: number): void {
  activeWindow.value = clamp(from, to);
}

function clearWindow(): void {
  activeWindow.value = null;
}

/**
 * Seeds the window from the route query (`?from=&to=`) so a windowed view can be
 * reloaded or shared by URL. Invalid or missing params clear the window.
 */
function initFromQuery(query: LocationQuery): void {
  const from = Number(query[QUERY_PARAM_FROM]);
  const to = Number(query[QUERY_PARAM_TO]);
  if (Number.isFinite(from) && Number.isFinite(to) && to > from) {
    activeWindow.value = clamp(from, to);
  } else {
    activeWindow.value = null;
  }
}

/**
 * Mirrors the active window into the URL without polluting browser history
 * (uses `replace`). Removes the params when the whole recording is selected.
 */
function syncToRouter(router: Router): void {
  const query = { ...router.currentRoute.value.query };
  if (activeWindow.value !== null) {
    query[QUERY_PARAM_FROM] = String(Math.round(activeWindow.value.from));
    query[QUERY_PARAM_TO] = String(Math.round(activeWindow.value.to));
  } else {
    delete query[QUERY_PARAM_FROM];
    delete query[QUERY_PARAM_TO];
  }
  router.replace({ query });
}

/**
 * Shared time-window context for a profile. Reading views call this to obtain the
 * resolved range; the Recording Overview navigator calls `setWindow`/`clearWindow`
 * and the URL sync helpers.
 */
export function useProfileTimeWindow(): {
  activeWindow: Ref<TimeWindow | null>;
  resolvedWindow: ComputedRef<TimeWindow>;
  recordingDurationMillis: ComputedRef<number>;
  isWindowed: ComputedRef<boolean>;
  windowFraction: ComputedRef<number>;
  setWindow: (from: number, to: number) => void;
  clearWindow: () => void;
  initFromQuery: (query: LocationQuery) => void;
  syncToRouter: (router: Router) => void;
} {
  // Drop a stale window when the active profile changes.
  const currentProfileId = profileStore.profileId.value;
  if (windowProfileId.value !== currentProfileId) {
    windowProfileId.value = currentProfileId;
    activeWindow.value = null;
  }

  return {
    activeWindow,
    resolvedWindow,
    recordingDurationMillis,
    isWindowed,
    windowFraction,
    setWindow,
    clearWindow,
    initFromQuery,
    syncToRouter
  };
}
