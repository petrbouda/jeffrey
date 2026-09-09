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

import type { LocationQueryValue } from 'vue-router';

/**
 * The query parameter that names a baseline on a profile URL.
 *
 * The differential views take their baseline from session storage, which until now only the
 * in-app picker wrote. That makes a comparison impossible to link to: the IntelliJ plugin can
 * open a profile but not the comparison a developer just set up in its recording panel. This
 * parameter is the one addition that closes it — `?baseline=<profileId>` on any profile route
 * seeds the same selection the picker would have made.
 */
export const BASELINE_QUERY_PARAM = 'baseline';

type QueryValue = LocationQueryValue | LocationQueryValue[] | undefined;

/**
 * The baseline profile id a URL asks for, or null when there is none to adopt.
 *
 * A profile is never its own baseline: `compare_list` rejects that pair outright, and the
 * differential views would subtract a recording from itself and render an empty tree that looks
 * like a finding. Rejecting it here means a malformed link falls back to the profile's own pages
 * rather than to a comparison that says nothing.
 */
export function baselineIdFromQuery(raw: QueryValue, primaryProfileId: string): string | null {
  const value = Array.isArray(raw) ? raw[0] : raw;
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  if (trimmed.length === 0 || trimmed === primaryProfileId) {
    return null;
  }
  return trimmed;
}
