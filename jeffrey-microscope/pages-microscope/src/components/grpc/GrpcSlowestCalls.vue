<template>
  <div>
    <SlowestCountHeader
      v-if="calls.length > 0"
      :shown="shownCount"
      :total="totalCallCount"
      note="sorted by response time"
    />
    <GrpcCallTable
      title="Slowest gRPC Calls"
      icon="clock-history"
      :calls="calls"
      :max-displayed="maxDisplayed"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import GrpcCallTable from '@/components/grpc/GrpcCallTable.vue';
import SlowestCountHeader from '@/components/SlowestCountHeader.vue';
import type { GrpcSlowCall } from '@/services/api/ProfileGrpcClient';

interface Props {
  calls: GrpcSlowCall[];
  totalCallCount: number;
  maxDisplayed?: number;
}

const props = withDefaults(defineProps<Props>(), {
  maxDisplayed: 20
});

const shownCount = computed(() => Math.min(props.calls.length, props.maxDisplayed));
</script>
