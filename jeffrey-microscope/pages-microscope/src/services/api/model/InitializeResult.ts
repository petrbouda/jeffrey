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

import type HeapSummary from '@/services/api/model/HeapSummary';
import type { SubPhaseTiming } from '@/services/api/model/InitPipelineResult';

/**
 * Response for POST /heap/initialize. `subPhases` carries the per-phase
 * timings from the index build that just ran — empty array when an existing
 * index was reused.
 */
export default interface InitializeResult {
  summary: HeapSummary;
  subPhases: SubPhaseTiming[];
}
