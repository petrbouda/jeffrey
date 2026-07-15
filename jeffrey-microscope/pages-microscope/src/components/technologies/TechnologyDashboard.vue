<template>
  <div>
    <CustomDisabledFeatureAlert v-if="isDisabled" :title="disabledTitle" :eventType="eventType" />

    <div v-else>
      <LoadingState v-if="isLoading" />

      <ErrorState v-else-if="error" :message="error" />

      <div v-else-if="data" class="dashboard-container">
        <slot :data="data" :reload="reload" />
      </div>

      <div v-else class="p-4 text-center">
        <h3 class="text-muted">{{ noDataTitle }}</h3>
        <p class="text-muted">{{ noDataMessage }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts" generic="T">
import { computed } from 'vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import CustomDisabledFeatureAlert from '@/components/alerts/CustomDisabledFeatureAlert.vue';
import { useTechnologyData } from '@/composables/useTechnologyData';

/**
 * Shared shell for technology dashboard views (HTTP/gRPC/JDBC/method-tracing).
 * Owns the disabled -> loading -> error -> data -> no-data state machine via
 * useTechnologyData; the view supplies the fetch function and the body through
 * the default slot, which receives the loaded data (and a reload function).
 */
interface Props {
  fetch: () => Promise<T>;
  disabled: boolean;
  disabledTitle: string;
  eventType: string;
  noDataTitle: string;
  noDataMessage: string;
}

const props = defineProps<Props>();

const isDisabled = computed(() => props.disabled);

const { data, isLoading, error, reload } = useTechnologyData<T>(() => props.fetch(), isDisabled);
</script>
