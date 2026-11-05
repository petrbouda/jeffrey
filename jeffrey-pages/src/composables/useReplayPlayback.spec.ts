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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/vue';
import { defineComponent, ref } from 'vue';
import { useReplayPlayback } from './useReplayPlayback';
import type { ReplayStep } from '@/types/pluginUsage';

const Player = defineComponent({
  setup() {
    const steps = ref<ReplayStep[]>([
      { t: 0, lane: 'main', kind: 'skill', name: 'analyze-jfr', input: '' },
      { t: 20000, lane: 'main', kind: 'jeffrey', name: 'profiles_list', input: '' },
      { t: 20100, lane: 'main', kind: 'jeffrey', name: 'profiles_get', input: '' }
    ]);
    return useReplayPlayback(steps);
  },
  template: `<button @click="toggle">{{ playing ? 'Pause' : 'Replay' }}</button>
    <select v-model="speed" aria-label="Speed"><option :value="1">Normal</option><option :value="4">4×</option></select>
    <output>{{ current?.name }}</output>`
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('Replay playback speed', () => {
  it('speeds up long compressed gaps and short minimum gaps', async () => {
    vi.useFakeTimers();
    render(Player);
    await fireEvent.update(screen.getByLabelText('Speed'), '4');
    await fireEvent.click(screen.getByRole('button', { name: 'Replay' }));
    expect(screen.getByRole('status').textContent).toBe('analyze-jfr');
    await vi.advanceTimersByTimeAsync(399);
    expect(screen.getByRole('status').textContent).toBe('analyze-jfr');
    await vi.advanceTimersByTimeAsync(1);
    expect(screen.getByRole('status').textContent).toBe('profiles_list');
    await vi.advanceTimersByTimeAsync(55);
    expect(screen.getByRole('status').textContent).toBe('profiles_get');
    expect(screen.getByRole('button', { name: 'Replay' })).toBeTruthy();
  });

  it('applies a speed change while a call is waiting', async () => {
    vi.useFakeTimers();
    render(Player);
    await fireEvent.click(screen.getByRole('button', { name: 'Replay' }));
    await fireEvent.update(screen.getByLabelText('Speed'), '4');
    await vi.advanceTimersByTimeAsync(400);
    expect(screen.getByRole('status').textContent).toBe('profiles_list');
  });
});
