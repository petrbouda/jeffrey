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

import { ref } from "vue";
import ProjectRepositoryClient from "@remote/services/api/ProjectRepositoryClient";
import RecordingSession from "@remote/services/api/model/RecordingSession";

/**
 * Sessions read one at a time, files included, as they are opened. A listing names its sessions
 * without reading them, so a view fetches a session when the reader opens it and keeps the
 * answer here.
 *
 * A read already in flight is shared rather than repeated, so a refresh that lands while the
 * reader is opening the same session costs one request, and an older answer can never
 * overwrite a newer one.
 */
export function useSessionReads(client: () => ProjectRepositoryClient) {
  const sessions = ref<Record<string, RecordingSession>>({});
  const loading = ref<Record<string, boolean>>({});
  const failed = ref<Record<string, boolean>>({});
  const inFlight = new Map<string, Promise<RecordingSession>>();

  function read(sessionId: string): Promise<RecordingSession> {
    const pending = inFlight.get(sessionId);
    if (pending) {
      return pending;
    }

    loading.value[sessionId] = true;
    const request = client()
      .getRecordingSession(sessionId)
      .then((session) => {
        sessions.value[sessionId] = session;
        failed.value[sessionId] = false;
        return session;
      })
      .catch((error) => {
        failed.value[sessionId] = true;
        throw error;
      })
      .finally(() => {
        inFlight.delete(sessionId);
        loading.value[sessionId] = false;
      });
    inFlight.set(sessionId, request);
    return request;
  }

  /** Forgets every session but these — the ones a refreshed listing still holds. */
  function retainOnly(sessionIds: Set<string>): void {
    for (const state of [sessions.value, loading.value, failed.value]) {
      for (const id of Object.keys(state)) {
        if (!sessionIds.has(id)) {
          delete state[id];
        }
      }
    }
  }

  return { sessions, loading, failed, read, retainOnly };
}
