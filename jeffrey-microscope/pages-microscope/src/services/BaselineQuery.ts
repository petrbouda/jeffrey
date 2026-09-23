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
