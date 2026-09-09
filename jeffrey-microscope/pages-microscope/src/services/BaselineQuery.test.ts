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

import { describe, expect, it } from 'vitest';
import { BASELINE_QUERY_PARAM, baselineIdFromQuery } from './BaselineQuery';

const PRIMARY = 'primary-profile-id';

describe('baselineIdFromQuery', () => {
  it('reads the id a link names', () => {
    expect(baselineIdFromQuery('baseline-profile-id', PRIMARY)).toBe('baseline-profile-id');
  });

  it('trims surrounding whitespace', () => {
    expect(baselineIdFromQuery('  baseline-profile-id  ', PRIMARY)).toBe('baseline-profile-id');
  });

  it('takes the first value when the parameter is repeated', () => {
    expect(baselineIdFromQuery(['first-id', 'second-id'], PRIMARY)).toBe('first-id');
  });

  it('has no baseline when the parameter is absent', () => {
    expect(baselineIdFromQuery(undefined, PRIMARY)).toBeNull();
  });

  it('has no baseline for a valueless parameter', () => {
    // vue-router hands `?baseline` with no value through as null.
    expect(baselineIdFromQuery(null, PRIMARY)).toBeNull();
  });

  it('has no baseline for a blank value', () => {
    expect(baselineIdFromQuery('   ', PRIMARY)).toBeNull();
  });

  it('refuses the primary as its own baseline', () => {
    // Subtracting a recording from itself renders an empty tree that reads like a finding, and
    // compare_list rejects the same pair outright.
    expect(baselineIdFromQuery(PRIMARY, PRIMARY)).toBeNull();
  });

  it('names the parameter the IntelliJ plugin writes', () => {
    // The plugin builds this URL from its own constant; the two spellings have to agree.
    expect(BASELINE_QUERY_PARAM).toBe('baseline');
  });
});
