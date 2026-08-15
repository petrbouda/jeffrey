<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
      with-timeline-link
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
