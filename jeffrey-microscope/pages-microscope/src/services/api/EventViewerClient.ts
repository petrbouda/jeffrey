/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import BaseProfileClient from '@/services/api/BaseProfileClient';
import EventType from '@/services/api/model/EventType.ts';
import EventFieldDescription from '@/services/api/model/EventFieldDescription.ts';
import EventTypeDescription from '@/services/api/model/EventTypeDescription.ts';

export default class EventViewerClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'viewer');
  }

  eventTypes(): Promise<EventTypeDescription[]> {
    return super.get<EventTypeDescription[]>('/events/types');
  }

  eventTypesTree(): Promise<EventType[]> {
    return super.get<EventType[]>('/events/types/tree');
  }

  events(eventType: string): Promise<Record<string, string | number>[]> {
    return super.get<Record<string, string | number>[]>('/events/' + eventType);
  }

  eventColumns(eventType: string): Promise<EventFieldDescription[]> {
    return super.get<EventFieldDescription[]>('/events/' + eventType + '/columns');
  }
}
