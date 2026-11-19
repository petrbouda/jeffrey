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

import type { LocationQueryValue } from 'vue-router';
import type EventTypeDescription from '@/services/api/model/EventTypeDescription';
import linkParams from '@/router/link-params.json';

/**
 * The query parameter that opens the events page on one event type. The MCP answers about an event
 * type (jfr_describeEventType, jfr_queryEvents) write it into their links, so the reader lands on the
 * events the agent read rather than on the type picker. The name comes from the link contract the
 * server's tests read.
 */
export const EVENT_TYPE_QUERY_PARAM = linkParams.events.EVENT_TYPE;

type QueryValue = LocationQueryValue | LocationQueryValue[] | undefined;

/**
 * The event type a link opens the page on, or null when there is none to open: no parameter, or a
 * type the page does not list - one the profile did not record, or recorded no events of.
 */
export function linkedEventType(
  raw: QueryValue,
  eventTypes: EventTypeDescription[]
): EventTypeDescription | null {
  const value = Array.isArray(raw) ? raw[0] : raw;
  if (typeof value !== 'string') {
    return null;
  }
  const code = value.trim();
  return eventTypes.find(eventType => eventType.code === code && eventType.count > 0) ?? null;
}

/**
 * Where the event types view leaves the type a reader picked there, for the events page to open once.
 */
export const SAVED_EVENT_TYPE_KEY = 'selectedEventType';

/** The part of `Storage` the events page reads its carried-over pick from. */
export type PickStorage = Pick<Storage, 'getItem' | 'removeItem'>;

/**
 * The event type the events page opens on: the one a link names, else the pick carried over from the
 * event types view, else none. A pick is consumed when a link or the pick itself decides the opening -
 * left in place, it would open a type on a later visit that asked for none.
 */
export function openingEventType(
  raw: QueryValue,
  eventTypes: EventTypeDescription[],
  storage: PickStorage
): EventTypeDescription | null {
  const pick = storage.getItem(SAVED_EVENT_TYPE_KEY);
  const linked = linkedEventType(raw, eventTypes);
  if (linked) {
    if (pick !== null) {
      storage.removeItem(SAVED_EVENT_TYPE_KEY);
    }
    return linked;
  }
  const code = pickedCode(pick);
  const picked =
    code == null ? null : (eventTypes.find(eventType => eventType.code === code) ?? null);
  if (picked) {
    storage.removeItem(SAVED_EVENT_TYPE_KEY);
  }
  return picked;
}

function pickedCode(pick: string | null): string | null {
  if (pick === null) {
    return null;
  }
  try {
    const parsed = JSON.parse(pick) as { code?: unknown };
    return typeof parsed.code === 'string' ? parsed.code : null;
  } catch {
    return null;
  }
}
