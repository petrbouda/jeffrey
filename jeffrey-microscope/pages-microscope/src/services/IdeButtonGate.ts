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

import IdeClient from '@/services/api/IdeClient';

/**
 * Per-(profile, class) cached check of whether the connected IDE contains a class. Used to enable the
 * flamegraph IDE buttons in JFR Profiler Plugin mode (they render disabled and enable only when the
 * class is present). Results are cached so re-hovering the same frame doesn't re-query the IDE; any
 * failure resolves to false so the buttons stay disabled.
 */
const cache = new Map<string, boolean>();

function key(profileId: string, fqn: string): string {
  return `${profileId}::${fqn}`;
}

async function check(profileId: string, fqn: string): Promise<boolean> {
  const cacheKey = key(profileId, fqn);
  const cached = cache.get(cacheKey);
  if (cached !== undefined) {
    return cached;
  }
  try {
    const response = await new IdeClient().hasClass(profileId, fqn);
    const found = response?.found === true;
    cache.set(cacheKey, found);
    return found;
  } catch {
    return false;
  }
}

export default {
  check,
};
