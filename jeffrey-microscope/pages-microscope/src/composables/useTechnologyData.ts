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

import { type ComputedRef, nextTick, onMounted, type Ref, ref } from 'vue';

/**
 * Composable for loading technology dashboard data with consistent loading/error/disabled state management.
 *
 * Eliminates boilerplate across gRPC, HTTP, JDBC, and Method Tracing technology views.
 *
 * @param fetchFn - Async function that fetches the data
 * @param isDisabled - Computed ref indicating whether the feature is disabled
 */
export function useTechnologyData<T>(
  fetchFn: () => Promise<T>,
  isDisabled: ComputedRef<boolean> | Ref<boolean>
) {
  const data = ref<T | null>(null) as Ref<T | null>;
  // A disabled source never loads, so it must not start out (and stay) loading.
  const isLoading = ref(!isDisabled.value);
  const error = ref<string | null>(null);

  const load = async () => {
    try {
      isLoading.value = true;
      error.value = null;
      data.value = await fetchFn();
      await nextTick();
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Unknown error occurred';
      console.error('Error loading technology data:', err);
    } finally {
      isLoading.value = false;
    }
  };

  onMounted(() => {
    if (!isDisabled.value) {
      load();
    }
  });

  return { data, isLoading, error, reload: load };
}
