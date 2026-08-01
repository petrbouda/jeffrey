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

import type { TimelinePhase } from '@shared/components/StageTimeline.vue';

/**
 * The heap-dump initialization pipeline as the user reads it: stages grouped into phases, with the
 * labels. The backend stores and reports stages by opaque id only, so this file is the single source of
 * truth for what any of them are called — renaming a heading here is a frontend-only change.
 */
export const HEAP_DUMP_PHASES: TimelinePhase[] = [
  {
    id: 'indexing',
    name: 'Heap Indexing',
    description: 'Loading and parsing the heap file',
    stages: [
      { id: 'load', label: 'Loading heap dump' },
      { id: 'parse', label: 'Parsing heap structure' },
      { id: 'index', label: 'Building indexes' }
    ]
  },
  {
    id: 'analysis',
    name: 'Memory Analysis',
    description: 'Strings, threads, dominator tree, leaks',
    stages: [
      { id: 'strings', label: 'Analyzing strings' },
      { id: 'dominator', label: 'Computing dominator tree' },
      { id: 'threads', label: 'Analyzing threads' },
      { id: 'biggest', label: 'Finding biggest objects' },
      { id: 'collections', label: 'Analyzing collections' },
      { id: 'leaks', label: 'Detecting leak suspects' }
    ]
  },
  {
    id: 'hotspots',
    name: 'Hotspots',
    description: 'Class loaders, biggest collections, waste',
    stages: [
      { id: 'classloaders', label: 'Analyzing class loaders' },
      { id: 'consumers', label: 'Aggregating memory consumers' },
      { id: 'duplicates', label: 'Detecting duplicate data' },
      { id: 'biggest-collections', label: 'Finding biggest collections' }
    ]
  }
];
