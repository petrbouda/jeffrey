/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import EventMetadata from '@/services/api/model/EventMetadata';

export default class ThreadMetadata {
  constructor(
    public lifespan: EventMetadata,
    public parked: EventMetadata,
    public blocked: EventMetadata,
    public waiting: EventMetadata,
    public sleep: EventMetadata,
    public socketRead: EventMetadata,
    public socketWrite: EventMetadata,
    public fileRead: EventMetadata,
    public fileWrite: EventMetadata
  ) {}
}
