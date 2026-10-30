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

/**
 * Where the remote browser stands: the workspace, project, instance and session picked, each
 * null until picked. A level is only ever set below a set one. It travels in the Remote page's
 * query, so a link opens the browser at the same session.
 */
export interface RemoteSelection {
  workspaceId: string | null;
  projectId: string | null;
  instanceId: string | null;
  sessionId: string | null;
}

export const EMPTY_SELECTION: RemoteSelection = Object.freeze({
  workspaceId: null,
  projectId: null,
  instanceId: null,
  sessionId: null
});

/** The query parameter of each level, in order from the top. */
export const SELECTION_PARAMS: Record<keyof RemoteSelection, string> = Object.freeze({
  workspaceId: 'workspaceId',
  projectId: 'projectId',
  instanceId: 'instanceId',
  sessionId: 'sessionId'
});

const LEVELS: (keyof RemoteSelection)[] = ['workspaceId', 'projectId', 'instanceId', 'sessionId'];

type QueryValue = string | null | (string | null)[] | undefined;

function single(value: QueryValue): string | null {
  const first = Array.isArray(value) ? value[0] : value;
  return typeof first === 'string' && first.length > 0 ? first : null;
}

/**
 * Reads a selection from a route query. A level below a missing one is dropped: a project means
 * nothing without its workspace.
 */
export function selectionFromQuery(query: Record<string, QueryValue>): RemoteSelection {
  const selection: RemoteSelection = { ...EMPTY_SELECTION };
  for (const level of LEVELS) {
    const value = single(query[SELECTION_PARAMS[level]]);
    if (value === null) {
      break;
    }
    selection[level] = value;
  }
  return selection;
}

/** The query that names a selection; levels not picked are left out. */
export function queryFromSelection(selection: RemoteSelection): Record<string, string> {
  const query: Record<string, string> = {};
  for (const level of LEVELS) {
    const value = selection[level];
    if (value === null) {
      break;
    }
    query[SELECTION_PARAMS[level]] = value;
  }
  return query;
}
