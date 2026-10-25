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
import HubClient from "@hubs/services/api/HubClient";
import Hub from "@hubs/services/api/model/Hub";
import { useHubReadOnly } from "@hubs/composables/useHubReadOnly";

/** Lets the lookup the composable started settle, whichever way it goes. */
function settled(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

function clientAnswering(answer: Promise<Hub>): HubClient {
  const client = new HubClient();
  vi.spyOn(client, "getById").mockReturnValue(answer);
  return client;
}

describe("useHubReadOnly", () => {
  it("is true for a hub that reports itself read-only", async () => {
    const readOnly = useHubReadOnly("share", clientAnswering(Promise.resolve({ readOnly: true } as Hub)));

    await settled();

    expect(readOnly.value).toBe(true);
  });

  it("is false for a hub that accepts changes", async () => {
    const readOnly = useHubReadOnly("grpc", clientAnswering(Promise.resolve({ readOnly: false } as Hub)));

    await settled();

    expect(readOnly.value).toBe(false);
  });

  it("stays false when the hub cannot be read", async () => {
    const readOnly = useHubReadOnly("gone", clientAnswering(Promise.reject(new Error("not found"))));

    await settled();

    expect(readOnly.value).toBe(false);
  });
});
