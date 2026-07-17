/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import RecordingEventSource from './api/model/RecordingEventSource';

/**
 * The single frontend source of truth for recording-format presentation and navigation. Every
 * place that used to branch on `eventSource === 'PPROF'` (card styling, list filters, upload
 * validation, profile landing route, profile nav sections) reads this descriptor instead — adding
 * a new format means adding one descriptor here plus its `RecordingEventSource` enum value.
 */

export type RecordingFormatKey = 'JFR' | 'PPROF' | 'HEAP';

/** Top-level profile navigation sections (the pills in ProfileDetail). */
export type ProfileSection = 'JVM' | 'Technologies' | 'Visualization' | 'HeapDump' | 'Tools';

export interface RecordingFormatDescriptor {
  key: RecordingFormatKey;
  /** Short tag rendered on recording cards (e.g. "JFR", "pprof", "Heap"). */
  label: string;
  /** Label of the list type-filter chip (e.g. "Heap Dumps"). */
  filterLabel: string;
  /** Wording used in the upload drop-zone hint (e.g. "JFR recordings"). */
  dropHintLabel: string;
  /** Full bootstrap-icons class for card icons (e.g. "bi bi-activity"). */
  icon: string;
  /** Bare bootstrap-icons name for filter chips (e.g. "bi-activity"). */
  filterIcon: string;
  /** Color variant used by badges and filter chips. */
  variant: string;
  /** Modifier class of the card type tag. */
  typeTagClass: string;
  /** Card modifier class driving format-specific accents; null for the default (JFR) look. */
  cardClass: string | null;
  /** Modifier class of the "Open profile" hover hint. */
  openHintClass: string;
  /** Filename suffixes accepted for upload. */
  uploadSuffixes: string[];
  /** Tokens for the file input `accept` attribute (single-extension form browsers understand). */
  acceptTokens: string[];
  /** Profile nav sections available for this format. */
  profileSections: ProfileSection[];
  /** Section to force-select when the profile opens; null keeps the URL-driven section. */
  initialSection: ProfileSection | null;
  /** Route of the profile's landing page. */
  profileLandingPath: (profileId: string) => string;
}

const JFR_FORMAT: RecordingFormatDescriptor = {
  key: 'JFR',
  label: 'JFR',
  filterLabel: 'JFR',
  dropHintLabel: 'JFR recordings',
  icon: 'bi bi-activity',
  filterIcon: 'bi-activity',
  variant: 'indigo',
  typeTagClass: 'rec-card__type--jfr',
  cardClass: null,
  openHintClass: 'rec-card__hint--open',
  uploadSuffixes: ['.jfr', '.jfr.lz4'],
  acceptTokens: ['.jfr', '.lz4'],
  profileSections: ['JVM', 'Technologies', 'Visualization', 'HeapDump', 'Tools'],
  initialSection: null,
  profileLandingPath: profileId => `/profiles/${profileId}/overview`
};

const PPROF_FORMAT: RecordingFormatDescriptor = {
  key: 'PPROF',
  label: 'pprof',
  filterLabel: 'pprof',
  dropHintLabel: 'pprof profiles',
  icon: 'bi bi-fire',
  filterIcon: 'bi-fire',
  variant: 'teal',
  typeTagClass: 'rec-card__type--pprof',
  cardClass: 'rec-card--pprof',
  openHintClass: 'rec-card__hint--open',
  uploadSuffixes: ['.pprof', '.pb.gz'],
  acceptTokens: ['.pprof', '.pb.gz'],
  // pprof carries stack samples only (no GC, JVM internals, heap or technology events), so the
  // profile exposes only the stack-based Visualization section and lands on the flamegraph.
  profileSections: ['Visualization'],
  initialSection: 'Visualization',
  profileLandingPath: profileId => `/profiles/${profileId}/flamegraphs/primary`
};

const HEAP_FORMAT: RecordingFormatDescriptor = {
  key: 'HEAP',
  label: 'Heap',
  filterLabel: 'Heap Dumps',
  dropHintLabel: 'heap dumps',
  icon: 'bi bi-pie-chart-fill',
  filterIcon: 'bi-pie-chart-fill',
  variant: 'purple',
  typeTagClass: 'rec-card__type--heap',
  cardClass: 'rec-card--heap-dump',
  openHintClass: 'rec-card__hint--open-heap',
  uploadSuffixes: ['.hprof', '.hprof.gz'],
  acceptTokens: ['.hprof', '.gz'],
  profileSections: ['HeapDump'],
  initialSection: 'HeapDump',
  profileLandingPath: profileId => `/profiles/${profileId}/heap-dump/settings`
};

/** All formats in display order (upload hints, list filter chips). */
export const RECORDING_FORMATS: RecordingFormatDescriptor[] = [JFR_FORMAT, PPROF_FORMAT, HEAP_FORMAT];

const FORMATS_BY_SOURCE: Partial<Record<RecordingEventSource, RecordingFormatDescriptor>> = {
  [RecordingEventSource.PPROF]: PPROF_FORMAT,
  [RecordingEventSource.HEAP_DUMP]: HEAP_FORMAT
};

/**
 * Resolves the format descriptor for a recording/profile event source. JFR is the default — it
 * also covers sources resolved later from the recording's content (JDK vs async-profiler).
 */
export function recordingFormatOf(eventSource?: string | null): RecordingFormatDescriptor {
  if (!eventSource) {
    return JFR_FORMAT;
  }
  return FORMATS_BY_SOURCE[eventSource as RecordingEventSource] ?? JFR_FORMAT;
}

/** Every accepted upload suffix across all formats. */
export const ALL_UPLOAD_SUFFIXES: string[] = RECORDING_FORMATS.flatMap(format => format.uploadSuffixes);

/** Value of the upload input's `accept` attribute across all formats. */
export const UPLOAD_ACCEPT_ATTRIBUTE: string = RECORDING_FORMATS.flatMap(
  format => format.acceptTokens
).join(',');
