<template>
  <GrpcCallTable
      title="Slowest gRPC Calls"
      icon="clock-history"
      :calls="calls"
      :max-displayed="maxDisplayed"
  />
</template>

<script setup lang="ts">
import GrpcCallTable from '@/components/grpc/GrpcCallTable.vue';
import type {GrpcSlowCall} from '@/services/api/ProfileGrpcClient';

interface Props {
  calls: GrpcSlowCall[];
  totalCallCount: number;
  maxDisplayed?: number;
}

withDefaults(defineProps<Props>(), {
  maxDisplayed: 20
});
</script>
