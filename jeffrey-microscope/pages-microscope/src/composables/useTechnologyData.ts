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
  const isLoading = ref(true);
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
