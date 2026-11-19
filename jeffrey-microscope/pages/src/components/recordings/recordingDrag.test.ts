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

import { describe, expect, it } from 'vitest';
import { droppedItem, type DraggedItem, startMove } from './recordingDrag';

/** A drag that carries what was set on it, the way the browser hands it from dragstart to drop. */
function drag(): DragEvent {
  const data = new Map<string, string>();
  const dataTransfer = {
    effectAllowed: 'none',
    setData: (format: string, value: string) => data.set(format, value),
    getData: (format: string) => data.get(format) ?? ''
  };
  return { dataTransfer } as unknown as DragEvent;
}

describe('recordingDrag', () => {
  it.each<DraggedItem>([
    { kind: 'recording', id: 'rec-1' },
    { kind: 'bundle', id: 'bundle-1' }
  ])('drops the  it was started with', item => {
    const event = drag();
    startMove(event, item);

    expect(droppedItem(event)).toEqual(item);
    expect(event.dataTransfer?.effectAllowed).toBe('move');
  });

  it('drops nothing when nothing was dragged', () => {
    expect(droppedItem(drag())).toBeNull();
  });

  it('drops nothing when the browser hands no data', () => {
    expect(droppedItem({ dataTransfer: null } as unknown as DragEvent)).toBeNull();
  });
});
