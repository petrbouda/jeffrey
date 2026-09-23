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
