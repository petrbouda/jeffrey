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

import { onUnmounted } from 'vue';

export interface Polling {
  /** Begins polling, or does nothing when it is already running. */
  start: () => void;
  /** Stops polling, or does nothing when it is already stopped. */
  stop: () => void;
  /** Whether a timer is currently running. */
  isRunning: () => boolean;
}

/**
 * Repeats `task` every `intervalMs` until stopped, and always stops when the calling
 * component unmounts — the part that is easy to forget when an interval is hand-rolled in
 * a view, and which leaves a timer firing against a component that no longer exists.
 *
 * <p>Starting and stopping are left to the caller so the decision to keep polling can
 * follow the data (poll while something is still running, stop once it finished) rather
 * than the lifecycle alone. Both are idempotent, so they can be called after every fetch
 * without tracking which state the timer is in.
 *
 * <p>Must be called during component setup, since it registers an unmount hook.
 */
export function usePolling(task: () => unknown, intervalMs: number): Polling {
  let timer: ReturnType<typeof setInterval> | null = null;

  const start = (): void => {
    if (timer !== null) {
      return;
    }
    timer = setInterval(task, intervalMs);
  };

  const stop = (): void => {
    if (timer === null) {
      return;
    }
    clearInterval(timer);
    timer = null;
  };

  onUnmounted(stop);

  return { start, stop, isRunning: () => timer !== null };
}
