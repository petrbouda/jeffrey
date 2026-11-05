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

import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue';
import type { ReplayStep } from '@/types/pluginUsage';

// Plays a run's calls one by one at their recorded pace, its gaps squeezed into a watchable range.

export const PLAYBACK_SPEEDS = [0.5, 1, 2, 4] as const;
export type PlaybackSpeed = (typeof PLAYBACK_SPEEDS)[number];

const MIN_DELAY_MS = 220;
const MAX_DELAY_MS = 1600;
const DEFAULT_SPEED: PlaybackSpeed = 1;

export function useReplayPlayback(steps: Ref<ReplayStep[]>) {
  const position = ref(0);
  const playing = ref(false);
  const speed = ref<PlaybackSpeed>(DEFAULT_SPEED);
  let timer: ReturnType<typeof setTimeout> | undefined;

  const lastIndex = computed(() => Math.max(0, steps.value.length - 1));
  const atEnd = computed(() => position.value >= lastIndex.value);
  const current = computed<ReplayStep | undefined>(() => steps.value[position.value]);

  function clearTimer(): void {
    if (timer !== undefined) {
      clearTimeout(timer);
      timer = undefined;
    }
  }

  function pause(): void {
    playing.value = false;
    clearTimer();
  }

  function seek(index: number): void {
    pause();
    position.value = Math.min(lastIndex.value, Math.max(0, index));
  }

  function stepBy(delta: number): void {
    seek(position.value + delta);
  }

  function delayToNext(): number {
    const here = steps.value[position.value]?.t ?? 0;
    const next = steps.value[position.value + 1]?.t ?? here;
    return Math.min(MAX_DELAY_MS, Math.max(MIN_DELAY_MS, (next - here) / speed.value));
  }

  function tick(): void {
    if (atEnd.value) {
      pause();
      return;
    }
    timer = setTimeout(() => {
      position.value += 1;
      tick();
    }, delayToNext());
  }

  /** Plays from where it stands, or from the first call once it has reached the last; pauses if playing. */
  function toggle(): void {
    if (playing.value) {
      pause();
      return;
    }
    if (atEnd.value) {
      position.value = 0;
    }
    playing.value = true;
    tick();
  }

  // A new run opens on its last call, so the whole run is on screen until it is played.
  watch(
    steps,
    () => {
      pause();
      position.value = lastIndex.value;
    },
    { immediate: true }
  );

  onBeforeUnmount(pause);

  return { position, playing, speed, atEnd, current, lastIndex, toggle, pause, seek, stepBy };
}

export type ReplayPlayback = ReturnType<typeof useReplayPlayback>;
