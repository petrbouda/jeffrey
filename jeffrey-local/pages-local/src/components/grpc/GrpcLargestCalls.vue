<template>
  <GrpcCallTable
      title="Largest gRPC Calls"
      icon="box-seam"
      :calls="calls"
      :show-total-size="true"
      :max-displayed="maxDisplayed"
  />
</template>

<script setup lang="ts">
import GrpcCallTable from '@/components/grpc/GrpcCallTable.vue';
import type {GrpcLargestCall} from '@/services/api/ProfileGrpcClient';

interface Props {
  calls: GrpcLargestCall[];
  totalCallCount: number;
  maxDisplayed?: number;
}

withDefaults(defineProps<Props>(), {
  maxDisplayed: 20
});
</script>
