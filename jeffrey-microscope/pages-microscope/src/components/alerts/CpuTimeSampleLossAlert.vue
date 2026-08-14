<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<!--
  The JDK CPU-time sampler reports its own drops. A graph drawn from what survived is still the
  best available picture, so this states the shortfall in one line rather than blocking the view.
  Renders nothing unless the event type is jdk.CPUTimeSample and the loss is worth acting on.
-->
<template>
  <div v-if="visible" class="alert alert-warning cpu-loss-alert d-flex align-items-start">
    <i class="bi bi-exclamation-triangle me-2"></i>
    <div>
      <span class="cpu-loss-headline">
        {{ lostSamples.toLocaleString() }} of {{ attemptedSamples.toLocaleString() }} CPU-time
        samples ({{ lossPercentage }}) never reached the recording.
      </span>
      <span class="cpu-loss-detail">
        The sampler queues each sample from a signal handler, and that queue overflowed
        {{ lossEvents.toLocaleString() }} times. The drops are not spread evenly — a thread that
        burns CPU in bursts loses more than a steady one, so treat the widths below as approximate.
        Raising <code>jdk.CPUTimeSample#throttle</code> in the recording settings trades resolution
        for a complete picture.
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import EventTypes from '@/services/EventTypes';
import SamplerHealthClient from '@/services/api/SamplerHealthClient';
import type CpuTimeSampleLoss from '@/services/api/model/CpuTimeSampleLoss';
import FormattingService from '@shared/services/FormattingService';

/**
 * Below this share of dropped samples the graph is close enough that a warning would be noise.
 */
const LOSS_WARNING_THRESHOLD = 0.01;

const props = defineProps<{
  profileId: string;
  /** The event type the graph is drawn from; anything but jdk.CPUTimeSample renders nothing. */
  eventType: string;
}>();

const loss = ref<CpuTimeSampleLoss | null>(null);

const lostSamples = computed(() => loss.value?.lostSamples ?? 0);
const lossEvents = computed(() => loss.value?.lossEvents ?? 0);
const attemptedSamples = computed(() => (loss.value?.capturedSamples ?? 0) + lostSamples.value);

const lossRatio = computed(() => {
  if (attemptedSamples.value === 0) {
    return 0;
  }
  return lostSamples.value / attemptedSamples.value;
});

const lossPercentage = computed(() => FormattingService.formatPercentage(lossRatio.value));

const visible = computed(() => loss.value !== null && lossRatio.value >= LOSS_WARNING_THRESHOLD);

onMounted(async () => {
  if (!EventTypes.isCpuTimeSample(props.eventType)) {
    return;
  }
  try {
    loss.value = await new SamplerHealthClient(props.profileId).cpuTimeSampleLoss();
  } catch {
    // An advisory note must never take the graph down with it, nor raise a toast of its own.
    loss.value = null;
  }
});
</script>

<style scoped>
.cpu-loss-alert {
  padding: 0.5rem 0.75rem;
  margin-bottom: 0.5rem;
  font-size: var(--font-size-sm);
  line-height: 1.5;
}

.cpu-loss-headline {
  font-weight: var(--font-weight-semibold);
}

.cpu-loss-detail {
  color: var(--color-text);
}

.cpu-loss-alert code {
  font-family: var(--font-family-monospace);
  color: var(--color-primary);
}
</style>
