<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->

<script setup lang="ts">
import { computed } from 'vue';
import { formatOffset } from '@/composables/usePluginUsage';
import { PLAYBACK_SPEEDS, type PlaybackSpeed } from '@/composables/useReplayPlayback';

// Play, pause, step and scrub through a run's calls.

const props = defineProps<{
  position: number;
  count: number;
  playing: boolean;
  atEnd: boolean;
  speed: PlaybackSpeed;
  offsetMs: number | null;
}>();

const emit = defineEmits<{
  (e: 'toggle'): void;
  (e: 'step', delta: number): void;
  (e: 'seek', index: number): void;
  (e: 'speed', value: PlaybackSpeed): void;
}>();

const playLabel = computed(() => {
  if (props.playing) {
    return 'Pause';
  }
  return props.atEnd ? 'Replay' : 'Play';
});

const progress = computed(() =>
  props.count ? `Call ${props.position + 1} of ${props.count} · ${formatOffset(props.offsetMs)}` : 'No calls'
);

const speedLabel = (speed: PlaybackSpeed): string => (speed === 1 ? 'Normal' : `${speed}×`);

function onSeek(event: Event): void {
  emit('seek', Number((event.target as HTMLInputElement).value));
}

function onSpeed(event: Event): void {
  emit('speed', Number((event.target as HTMLSelectElement).value) as PlaybackSpeed);
}
</script>

<template>
  <div class="player">
    <button type="button" class="player-play" :disabled="!count" @click="emit('toggle')">
      <i class="bi" :class="playing ? 'bi-pause-fill' : atEnd ? 'bi-arrow-counterclockwise' : 'bi-play-fill'" aria-hidden="true"></i>
      {{ playLabel }}
    </button>
    <button type="button" class="player-step" :disabled="position <= 0" aria-label="Previous call" @click="emit('step', -1)">
      <i class="bi bi-chevron-left" aria-hidden="true"></i>
    </button>
    <button type="button" class="player-step" :disabled="atEnd" aria-label="Next call" @click="emit('step', 1)">
      <i class="bi bi-chevron-right" aria-hidden="true"></i>
    </button>
    <label class="player-speed">
      <span class="visually-hidden">Speed</span>
      <select :value="speed" @change="onSpeed">
        <option v-for="value in PLAYBACK_SPEEDS" :key="value" :value="value">{{ speedLabel(value) }}</option>
      </select>
    </label>
    <label class="player-scrub">
      <span class="visually-hidden">Position in the run</span>
      <input type="range" min="0" :max="Math.max(0, count - 1)" :value="position" :disabled="!count" @input="onSeek" />
    </label>
    <span class="player-progress">{{ progress }}</span>
  </div>
</template>

<style scoped>
.player {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.player button,
.player-speed select {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--site-line-strong);
  border-radius: var(--site-radius-sm);
  background: var(--site-surface);
  color: var(--site-ink);
  font-size: 13.5px;
  cursor: pointer;
}

.player button:disabled {
  opacity: 0.45;
  cursor: default;
}

.player .player-play {
  min-width: 96px;
  justify-content: center;
  border-color: var(--site-flame);
  background: var(--site-flame);
  color: var(--site-flame-ink);
  font-weight: 600;
}

.player .player-play:hover:not(:disabled) {
  background: var(--site-ember);
  border-color: var(--site-ember);
}

.player-scrub {
  flex: 1;
  min-width: 140px;
  display: flex;
}

.player-scrub input {
  width: 100%;
  accent-color: var(--site-flame);
}

.player-progress {
  font-size: 12.5px;
  color: var(--site-faint);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
