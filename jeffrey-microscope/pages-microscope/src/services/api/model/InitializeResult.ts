/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the GNU Affero General Public License v3.
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
