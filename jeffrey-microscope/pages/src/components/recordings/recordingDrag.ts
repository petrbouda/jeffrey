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

/**
 * The payload of a recording card or a bundle dragged onto a group chip: one place writes it and
 * one place reads it, so the chip always tells a bundle from a recording the way the card meant it.
 */

/** What can be moved to another group. */
export type DraggedItem = { kind: 'recording'; id: string } | { kind: 'bundle'; id: string };

const DRAG_FORMAT = 'text/plain';
const BUNDLE_PREFIX = 'bundle:';

/** Starts moving an item to another group. */
export function startMove(event: DragEvent, item: DraggedItem): void {
  if (!event.dataTransfer) {
    return;
  }
  event.dataTransfer.effectAllowed = 'move';
  event.dataTransfer.setData(
    DRAG_FORMAT,
    item.kind === 'bundle' ? BUNDLE_PREFIX + item.id : item.id
  );
}

/** The item a drop carries; null when it carries none. */
export function droppedItem(event: DragEvent): DraggedItem | null {
  const payload = event.dataTransfer?.getData(DRAG_FORMAT);
  if (!payload) {
    return null;
  }
  if (payload.startsWith(BUNDLE_PREFIX)) {
    return { kind: 'bundle', id: payload.slice(BUNDLE_PREFIX.length) };
  }
  return { kind: 'recording', id: payload };
}
