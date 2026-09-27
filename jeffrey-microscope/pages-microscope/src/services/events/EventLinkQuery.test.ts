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
import EventTypeDescription from '@/services/api/model/EventTypeDescription';
import { linkedEventType, openingEventType, SAVED_EVENT_TYPE_KEY } from './EventLinkQuery';

const ALLOCATION = new EventTypeDescription('Allocation Sample', 'jdk.ObjectAllocationSample', 12);
const CPU = new EventTypeDescription('Execution Sample', 'jdk.ExecutionSample', 40);
const EMPTY = new EventTypeDescription('Thread Park', 'jdk.ThreadPark', 0);
const RECORDED = [ALLOCATION, CPU, EMPTY];

describe('linkedEventType', () => {
  it('opens the event type a link names', () => {
    expect(linkedEventType('jdk.ExecutionSample', RECORDED)).toBe(CPU);
  });

  it('takes the first value when the parameter is repeated', () => {
    expect(linkedEventType(['jdk.ObjectAllocationSample', 'jdk.ExecutionSample'], RECORDED)).toBe(
      ALLOCATION
    );
  });

  it('trims surrounding whitespace', () => {
    expect(linkedEventType('  jdk.ExecutionSample ', RECORDED)).toBe(CPU);
  });

  it('opens nothing without the parameter', () => {
    expect(linkedEventType(undefined, RECORDED)).toBeNull();
    expect(linkedEventType(null, RECORDED)).toBeNull();
  });

  it('opens nothing for a type the profile did not record', () => {
    expect(linkedEventType('jdk.GarbageCollection', RECORDED)).toBeNull();
  });

  it('opens nothing for a type with no events, which the page does not list', () => {
    expect(linkedEventType('jdk.ThreadPark', RECORDED)).toBeNull();
  });
});

/** A storage double holding at most the one entry the events page reads. */
class FakeStorage {
  readonly removed: string[] = [];

  constructor(private readonly entries: Map<string, string>) {}

  getItem(key: string): string | null {
    return this.entries.get(key) ?? null;
  }

  removeItem(key: string): void {
    this.removed.push(key);
    this.entries.delete(key);
  }
}

function saved(code: string): FakeStorage {
  return new FakeStorage(new Map([[SAVED_EVENT_TYPE_KEY, JSON.stringify({ code })]]));
}

describe('openingEventType', () => {
  it('opens the linked type and clears a pick left over from the event types view', () => {
    const storage = saved('jdk.ObjectAllocationSample');

    expect(openingEventType('jdk.ExecutionSample', RECORDED, storage)).toBe(CPU);
    // The pick was made for another visit; left in place it would open on the next plain visit.
    expect(storage.getItem(SAVED_EVENT_TYPE_KEY)).toBeNull();
  });

  it('opens the linked type without touching storage that holds no pick', () => {
    const storage = new FakeStorage(new Map());

    expect(openingEventType('jdk.ExecutionSample', RECORDED, storage)).toBe(CPU);
    expect(storage.removed).toEqual([]);
  });

  it('opens the pick from the event types view once, without a link', () => {
    const storage = saved('jdk.ObjectAllocationSample');

    expect(openingEventType(undefined, RECORDED, storage)).toBe(ALLOCATION);
    expect(storage.getItem(SAVED_EVENT_TYPE_KEY)).toBeNull();
  });

  it('opens nothing for a pick the profile did not record, and leaves it', () => {
    const storage = saved('jdk.GarbageCollection');

    expect(openingEventType(undefined, RECORDED, storage)).toBeNull();
    expect(storage.removed).toEqual([]);
  });

  it('opens nothing for an unreadable pick', () => {
    const storage = new FakeStorage(new Map([[SAVED_EVENT_TYPE_KEY, '{not json']]));

    expect(openingEventType(undefined, RECORDED, storage)).toBeNull();
  });
});
