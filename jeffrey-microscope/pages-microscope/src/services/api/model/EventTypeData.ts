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
 * Data associated with an event type
 */
export default class EventTypeData {
  constructor(
    /**
     * Categories this event type belongs to
     */
    public categories: string[],
    /**
     * Display name of the event type
     */
    public name: string,
    /**
     * Whether this is a leaf node
     */
    public leaf: boolean,
    /**
     * Event source information JDK / Async-Profiler
     */
    public source: string,
    /**
     * Event type code (e.g. "jdk.CPULoad")
     * Optional as category nodes might not have a code
     */
    public code?: string,
    /**
     * Number of occurrences of this event type
     * Optional as category nodes might not have a count
     */
    public count?: number,
    /**
     * Whether this event type includes stack trace information
     * Optional as category nodes might not have this flag
     */
    public withStackTrace?: boolean
  ) {}
}
