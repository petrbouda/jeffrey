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

import type { StageChipGroup } from '@shared/components/StageChips.vue';
import type { Variant } from '@shared/types/ui';
import RecordingFileType from '../services/api/model/RecordingFileType';
import { HEAP_DUMP_INIT_CHIPS, PROFILE_INIT_CHIPS } from './profileInitChips';

/** The filter key a kind is counted and selected under in the recordings list. */
export type RecordingKindKey = 'JFR' | 'PPROF' | 'OTLP' | 'HEAP' | 'PERF';

/**
 * Everything the recordings list shows differently per kind of recording, in one place: how a card is
 * tagged and coloured, which stage chips follow its initialization, and which filter it counts under.
 * A recording is one kind; a bundle shows a badge per item and a card per item still to initialize.
 */
export interface RecordingKind {
  key: RecordingKindKey;
  /** The short tag on the card. */
  label: string;
  /** What the kind is called in prose — a bundle's item list, a filter. */
  name: string;
  icon: string;
  tagClass: string;
  /** The card's accent; null for JFR, the card's default. */
  cardClass: string | null;
  /** The kind's badge colour, where a bundle lists the kinds it holds. */
  badgeVariant: Variant;
  /** The type of the file a recording of this kind is read from, for a row that lists no file. */
  fileType: RecordingFileType;
  /** The stages its initialization shows; none for a kind built within the request. */
  chips: StageChipGroup[];
}

const JFR_KIND: RecordingKind = {
  key: 'JFR',
  label: 'JFR',
  name: 'JFR recording',
  icon: 'bi bi-activity',
  tagClass: 'rec-card__type--jfr',
  cardClass: null,
  badgeVariant: 'indigo',
  fileType: RecordingFileType.JFR,
  chips: PROFILE_INIT_CHIPS
};

const KINDS_BY_SOURCE: Record<string, RecordingKind> = {
  HEAP_DUMP: {
    key: 'HEAP',
    label: 'Heap',
    name: 'Heap dump',
    icon: 'bi bi-pie-chart-fill',
    tagClass: 'rec-card__type--heap',
    cardClass: 'rec-card--heap-dump',
    badgeVariant: 'purple',
    fileType: RecordingFileType.HEAP_DUMP,
    chips: HEAP_DUMP_INIT_CHIPS
  },
  PPROF: {
    key: 'PPROF',
    label: 'PPROF',
    name: 'pprof profile',
    icon: 'bi bi-bar-chart-fill',
    tagClass: 'rec-card__type--pprof',
    cardClass: 'rec-card--pprof',
    badgeVariant: 'teal',
    fileType: RecordingFileType.PPROF,
    chips: PROFILE_INIT_CHIPS
  },
  OPEN_TELEMETRY: {
    key: 'OTLP',
    label: 'OTLP',
    name: 'OpenTelemetry profile',
    icon: 'bi bi-broadcast',
    tagClass: 'rec-card__type--otel',
    cardClass: 'rec-card--otel',
    badgeVariant: 'orange',
    fileType: RecordingFileType.OTLP_PROFILE,
    chips: PROFILE_INIT_CHIPS
  },
  PERF_COUNTERS: {
    key: 'PERF',
    label: 'Perf Counters',
    name: 'Perf counters',
    icon: 'bi bi-speedometer2',
    tagClass: 'rec-card__type--perf',
    cardClass: 'rec-card--perf',
    badgeVariant: 'blue',
    fileType: RecordingFileType.PERF_COUNTERS,
    chips: []
  }
};

/** The kind of a recording, from its event source; anything not listed is a JFR recording. */
export function recordingKindOf(eventSource: string | null | undefined): RecordingKind {
  return (eventSource && KINDS_BY_SOURCE[eventSource]) || JFR_KIND;
}
