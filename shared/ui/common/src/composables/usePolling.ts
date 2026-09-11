/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
