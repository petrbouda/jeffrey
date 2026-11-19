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

import GCGenerationType from '@/services/api/model/GCGenerationType.ts';

export default class GCEvent {
  constructor(
    public timestamp: number,
    public gcId: number,
    public generationType: GCGenerationType,
    public collectorName: string,
    public type: string,
    public cause: string,
    public duration: number,
    public beforeGC: number,
    public afterGC: number,
    public freed: number,
    public efficiency: number,
    public heapSize: number,
    public sumOfPauses: number,
    public longestPause: number,
    public concurrent: boolean
  ) {}
}
