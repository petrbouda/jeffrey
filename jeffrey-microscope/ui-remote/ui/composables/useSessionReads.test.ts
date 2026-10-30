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
import { describe, expect, it, vi } from "vitest";
import ProjectRepositoryClient from "@remote/services/api/ProjectRepositoryClient";
import RecordingSession from "@remote/services/api/model/RecordingSession";
import { useSessionReads } from "@remote/composables/useSessionReads";

function session(id: string): RecordingSession {
  return new RecordingSession(id, id, "pod-1", 0, null, false, null, []);
}

describe("useSessionReads", () => {
  it("shares a read already in flight rather than asking twice", async () => {
    const client = new ProjectRepositoryClient("ws", "project");
    const read = vi.spyOn(client, "getRecordingSession").mockResolvedValue(session("s-1"));
    const reads = useSessionReads(() => client);

    await Promise.all([reads.read("s-1"), reads.read("s-1")]);

    expect(read).toHaveBeenCalledTimes(1);
    expect(reads.sessions.value["s-1"]?.id).toBe("s-1");
    expect(reads.failed.value["s-1"]).toBe(false);
  });
});
