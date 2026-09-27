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
import { initialVisibleWindow } from './InitialVisibleWindow';
import TimeConverter from './TimeConverter';
import TimeRange from '@/services/api/model/TimeRange';

const SECONDS = new TimeConverter('seconds');
const DATA = { min: 0, max: 600 };
const FIFTEEN_MINUTES_SECONDS = 900;
const FIVE_MINUTES_SECONDS = 300;

describe('initialVisibleWindow', () => {
  it('opens on the configured visible span from the start of the data', () => {
    expect(initialVisibleWindow(DATA, FIVE_MINUTES_SECONDS, null, SECONDS)).toEqual({
      start: 0,
      end: 300
    });
  });

  it('shows all of the data when it is shorter than the visible span', () => {
    expect(initialVisibleWindow(DATA, FIFTEEN_MINUTES_SECONDS, null, SECONDS)).toEqual({
      start: 0,
      end: 600
    });
  });

  it('opens on the window a link named, converted to the chart time unit', () => {
    const linked = new TimeRange(120_000, 180_000, false);

    expect(initialVisibleWindow(DATA, FIVE_MINUTES_SECONDS, linked, SECONDS)).toEqual({
      start: 120,
      end: 180
    });
  });

  it('keeps a linked window inside the data', () => {
    const linked = new TimeRange(500_000, 700_000, false);

    expect(initialVisibleWindow(DATA, FIVE_MINUTES_SECONDS, linked, SECONDS)).toEqual({
      start: 500,
      end: 600
    });
  });

  it('falls back to the visible span when a linked window misses the data entirely', () => {
    const linked = new TimeRange(700_000, 800_000, false);

    expect(initialVisibleWindow(DATA, FIVE_MINUTES_SECONDS, linked, SECONDS)).toEqual({
      start: 0,
      end: 300
    });
  });
});
