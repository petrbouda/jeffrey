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

import { ref, type Ref } from 'vue';

/**
 * One level of the remote browser — a listing read off the remote, with its loading and error
 * state. Only the newest read counts: a reader who picks another folder while the previous one is
 * still being listed never sees the older answer land on top of the newer.
 */
export interface Listing<T> {
  items: Ref<T[]>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  /**
   * Reads the listing and keeps what `pick` takes from the answer. Resolves to the answer, or to
   * undefined when the read failed or a newer one replaced it — the caller then stops there.
   */
  load<R>(read: () => Promise<R>, pick: (answer: R) => T[]): Promise<R | undefined>;
  /** Empties the listing and forgets any read still in flight. */
  clear(): void;
}

export function useListing<T>(failureMessage: string): Listing<T> {
  const items = ref<T[]>([]) as Ref<T[]>;
  const loading = ref(false);
  const error = ref<string | null>(null);
  let generation = 0;

  async function load<R>(read: () => Promise<R>, pick: (answer: R) => T[]): Promise<R | undefined> {
    const mine = ++generation;
    loading.value = true;
    error.value = null;
    try {
      const answer = await read();
      if (mine !== generation) {
        return undefined;
      }
      items.value = pick(answer);
      return answer;
    } catch (failure: any) {
      if (mine === generation) {
        items.value = [];
        error.value = failure?.response?.data?.message ?? failure?.message ?? failureMessage;
      }
      return undefined;
    } finally {
      if (mine === generation) {
        loading.value = false;
      }
    }
  }

  function clear(): void {
    generation++;
    items.value = [];
    loading.value = false;
    error.value = null;
  }

  return { items, loading, error, load, clear };
}
