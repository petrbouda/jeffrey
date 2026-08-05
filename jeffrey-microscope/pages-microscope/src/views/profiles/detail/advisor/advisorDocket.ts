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

import type { Variant } from '@shared/types/ui';
import type { AdvisorEventType } from '@/services/api/model/Advisor';
import { compareEventTypes } from '@/views/profiles/detail/advisor/eventTypeStyle';

/** The grade shown on a card, when the page has one to show. Prompts do not. */
export interface DocketBadge {
  value: string;
  variant: Variant;
}

/** One card of the Advisor's docket: a type, whether the recording carries it, and its grade. */
export interface DocketItem {
  eventType: string;
  label: string;
  available: boolean;
  badge?: DocketBadge;
}

/**
 * Every type the Advisor knows about in the canonical order, so the docket reads the same on Prompts,
 * Recommendations and Patches, and on every profile — a recording that carries no wall-clock samples
 * still shows Wall-Clock in second place, marked as missing rather than silently dropped.
 */
export function docketItems(
  eventTypes: AdvisorEventType[],
  badgeFor: (eventType: string) => DocketBadge | undefined
): DocketItem[] {
  return [...eventTypes]
    .sort((left, right) => compareEventTypes(left.eventType, right.eventType))
    .map(type => ({
      eventType: type.eventType,
      label: type.label,
      available: type.available,
      badge: type.available ? badgeFor(type.eventType) : undefined
    }));
}

/** The size of a proposed change, counted off the unified diff the page already holds. */
export interface PatchStats {
  files: number;
  added: number;
  removed: number;
}

const NO_PATCH_STATS: PatchStats = { files: 0, added: 0, removed: 0 };

const NEW_FILE_HEADER_PREFIX = '+++ ';
const OLD_FILE_HEADER_PREFIX = '--- ';
const ADDED_LINE_PREFIX = '+';
const REMOVED_LINE_PREFIX = '-';
const NO_FILE_PATH = '/dev/null';
const LINE_SEPARATOR = '\n';

/**
 * Counts the files and lines a unified diff touches. The two file headers are checked before the line
 * prefixes because `+++` and `---` would otherwise be counted as an added and a removed line each,
 * inflating every patch by one per file.
 */
export function patchStats(patch: string | null): PatchStats {
  if (patch === null || patch.trim() === '') {
    return NO_PATCH_STATS;
  }

  let files = 0;
  let added = 0;
  let removed = 0;

  for (const line of patch.split(LINE_SEPARATOR)) {
    if (line.startsWith(NEW_FILE_HEADER_PREFIX)) {
      if (!line.endsWith(NO_FILE_PATH)) {
        files += 1;
      }
    } else if (line.startsWith(OLD_FILE_HEADER_PREFIX)) {
      continue;
    } else if (line.startsWith(ADDED_LINE_PREFIX)) {
      added += 1;
    } else if (line.startsWith(REMOVED_LINE_PREFIX)) {
      removed += 1;
    }
  }

  return { files, added, removed };
}

export function hasPatch(patch: string | null): boolean {
  return patch !== null && patch.trim() !== '';
}
