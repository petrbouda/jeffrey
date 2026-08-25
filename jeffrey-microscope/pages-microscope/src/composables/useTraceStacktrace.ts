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

import { ref, watch, type Ref } from 'vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import type { TraceStackFrameRow } from '@/services/api/model/trace/TraceModels';

/**
 * One throw's stack, fetched once per profile and stack id.
 *
 * Cached because the same stack is asked for repeatedly and never changes: hovering the rail asks
 * for a preview, opening the span asks for the whole thing, and a call site that threw in ten spans
 * is one id in all ten. A profile's stacks are immutable once parsed, so nothing invalidates this
 * short of leaving the page.
 */
const cache = new Map<string, Promise<TraceStackFrameRow[]>>();

function load(profileId: string, stacktraceId: string): Promise<TraceStackFrameRow[]> {
  const key = `${profileId}/${stacktraceId}`;
  const hit = cache.get(key);
  if (hit) {
    return hit;
  }

  // The promise is cached, not the result, so two components asking at once share one request.
  // A failure is evicted so the next opener retries rather than inheriting the error for good.
  const pending = new ProfileTracesClient(profileId)
    .getStacktrace(stacktraceId)
    .then(stacktrace => stacktrace.frames)
    .catch(error => {
      cache.delete(key);
      throw error;
    });

  cache.set(key, pending);
  return pending;
}

export interface TraceStacktraceState {
  frames: Ref<TraceStackFrameRow[]>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
}

/**
 * Fetches the stack for `stacktraceId`, following the ref as it changes.
 *
 * A null id means the throw has no stack — an ordinary case, not an error — so it settles to an
 * empty list without a request.
 */
export function useTraceStacktrace(
  profileId: string,
  stacktraceId: Ref<string | null | undefined>
): TraceStacktraceState {
  const frames = ref<TraceStackFrameRow[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  watch(
    stacktraceId,
    id => {
      if (!id) {
        frames.value = [];
        loading.value = false;
        error.value = null;
        return;
      }

      loading.value = true;
      error.value = null;
      // The id at request time, so a slow response for a stack the reader has already moved on
      // from does not overwrite the one they are looking at now.
      const requested = id;
      load(profileId, requested)
        .then(loaded => {
          if (stacktraceId.value !== requested) {
            return;
          }
          frames.value = loaded;
        })
        .catch(() => {
          if (stacktraceId.value !== requested) {
            return;
          }
          error.value = 'The stack could not be loaded.';
        })
        .finally(() => {
          if (stacktraceId.value === requested) {
            loading.value = false;
          }
        });
    },
    { immediate: true }
  );

  return { frames, loading, error };
}
