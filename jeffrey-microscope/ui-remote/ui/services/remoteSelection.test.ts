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
  EMPTY_SELECTION,
  queryFromSelection,
  selectionFromQuery
} from '@remote/services/remoteSelection.ts';

const FULL = {
  workspaceId: 'UAT',
  projectId: 'vogon-das',
  instanceId: 'vogon-data-access-service-8587998d65-cxk6j',
  sessionId: '20261029-163112'
};

describe('selectionFromQuery', () => {
  it('reads every level', () => {
    expect(selectionFromQuery({ ...FULL })).toEqual(FULL);
  });

  it('reads nothing from an empty query', () => {
    expect(selectionFromQuery({})).toEqual(EMPTY_SELECTION);
  });

  it('drops a level below a missing one', () => {
    expect(
      selectionFromQuery({ workspaceId: 'UAT', instanceId: FULL.instanceId, sessionId: 'x' })
    ).toEqual({ ...EMPTY_SELECTION, workspaceId: 'UAT' });
  });

  it('treats an empty value as missing', () => {
    expect(selectionFromQuery({ workspaceId: 'UAT', projectId: '' })).toEqual({
      ...EMPTY_SELECTION,
      workspaceId: 'UAT'
    });
  });

  it('takes the first of a repeated parameter', () => {
    expect(selectionFromQuery({ workspaceId: ['UAT', 'PROD'] })).toEqual({
      ...EMPTY_SELECTION,
      workspaceId: 'UAT'
    });
  });
});

describe('queryFromSelection', () => {
  it('names every picked level', () => {
    expect(queryFromSelection(FULL)).toEqual(FULL);
  });

  it('leaves out the levels not picked', () => {
    expect(
      queryFromSelection({ ...EMPTY_SELECTION, workspaceId: 'UAT', projectId: 'vogon-das' })
    ).toEqual({ workspaceId: 'UAT', projectId: 'vogon-das' });
  });

  it('is empty for an empty selection', () => {
    expect(queryFromSelection(EMPTY_SELECTION)).toEqual({});
  });

  it('round-trips through the query', () => {
    expect(selectionFromQuery(queryFromSelection(FULL))).toEqual(FULL);
  });
});
