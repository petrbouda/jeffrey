<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

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
import SlowestCountHeader from '@shared/components/SlowestCountHeader.vue';
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
