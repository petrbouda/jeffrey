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
import {
  END_EPOCH_MS_QUERY_PARAM,
  SEARCH_QUERY_PARAM,
  START_EPOCH_MS_QUERY_PARAM,
  linkedGraphState
} from './FlamegraphLinkQuery';

const RECORDING_START = Date.UTC(2026, 2, 1, 12);
const RECORDING = { startEpochMillis: RECORDING_START, durationMillis: 60_000 };

describe('linkedGraphState', () => {
  describe('the window', () => {
    it('is the relative range the flamegraph request takes, placed on the recording start', () => {
      const state = linkedGraphState(
        {
          [START_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START + 10_000),
          [END_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START + 20_000)
        },
        RECORDING
      );

      expect(state.timeRange).toEqual({ start: 10_000, end: 20_000, absoluteTime: false });
    });

    it('takes a missing end as the end of the recording', () => {
      const state = linkedGraphState(
        { [START_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START + 45_000) },
        RECORDING
      );

      expect(state.timeRange).toEqual({ start: 45_000, end: 60_000, absoluteTime: false });
    });

    it('takes a missing start as the start of the recording', () => {
      const state = linkedGraphState(
        { [END_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START + 5_000) },
        RECORDING
      );

      expect(state.timeRange).toEqual({ start: 0, end: 5_000, absoluteTime: false });
    });

    it('clamps bounds that reach outside the recording', () => {
      const state = linkedGraphState(
        {
          [START_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START - 5_000),
          [END_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START + 90_000)
        },
        RECORDING
      );

      expect(state.timeRange).toEqual({ start: 0, end: 60_000, absoluteTime: false });
    });

    it('is absent without either bound, which keeps the opening zoom of today', () => {
      expect(linkedGraphState({ eventType: 'jdk.ExecutionSample' }, RECORDING).timeRange).toBeNull();
    });

    it('is absent when the recording has no span to place it on', () => {
      const state = linkedGraphState(
        { [START_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START + 10_000) },
        null
      );

      expect(state.timeRange).toBeNull();
    });

    it('ignores a bound that is not a whole number of milliseconds', () => {
      const state = linkedGraphState(
        {
          [START_EPOCH_MS_QUERY_PARAM]: 'soon',
          [END_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START + 20_000)
        },
        RECORDING
      );

      expect(state.timeRange).toEqual({ start: 0, end: 20_000, absoluteTime: false });
    });

    it('is absent when it ends before it starts', () => {
      const state = linkedGraphState(
        {
          [START_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START + 30_000),
          [END_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START + 10_000)
        },
        RECORDING
      );

      expect(state.timeRange).toBeNull();
    });

    it('takes the first value of a repeated bound', () => {
      const state = linkedGraphState(
        {
          [START_EPOCH_MS_QUERY_PARAM]: [
            String(RECORDING_START + 1_000),
            String(RECORDING_START + 2_000)
          ],
          [END_EPOCH_MS_QUERY_PARAM]: String(RECORDING_START + 3_000)
        },
        RECORDING
      );

      expect(state.timeRange).toEqual({ start: 1_000, end: 3_000, absoluteTime: false });
    });
  });

  describe('the search', () => {
    it('is the expression the link names, trimmed as the search box trims it', () => {
      expect(linkedGraphState({ [SEARCH_QUERY_PARAM]: '  OrderService$Batch ' }, RECORDING).search).toBe(
        'OrderService$Batch'
      );
    });

    it('is absent when the link names none', () => {
      expect(linkedGraphState({}, RECORDING).search).toBeNull();
    });

    it('is absent for a blank or valueless parameter', () => {
      expect(linkedGraphState({ [SEARCH_QUERY_PARAM]: '   ' }, RECORDING).search).toBeNull();
      expect(linkedGraphState({ [SEARCH_QUERY_PARAM]: null }, RECORDING).search).toBeNull();
    });

    it('does not need a recording span', () => {
      expect(linkedGraphState({ [SEARCH_QUERY_PARAM]: 'Orders' }, null).search).toBe('Orders');
    });
  });

  it('spells the parameters the MCP links write', () => {
    // FlamegraphViewLink on the server builds these names from its own constants.
    expect([START_EPOCH_MS_QUERY_PARAM, END_EPOCH_MS_QUERY_PARAM, SEARCH_QUERY_PARAM]).toEqual([
      'startEpochMs',
      'endEpochMs',
      'search'
    ]);
  });
});
