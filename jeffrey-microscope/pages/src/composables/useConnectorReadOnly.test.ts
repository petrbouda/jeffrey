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
import ConnectorClient from "@/services/api/connector/ConnectorClient";
import Connector from "@/services/api/model/connector/Connector";
import { useConnectorReadOnly } from "@/composables/useConnectorReadOnly";

/** Lets the lookup the composable started settle, whichever way it goes. */
function settled(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

function clientAnswering(answer: Promise<Connector | null>): ConnectorClient {
  const client = new ConnectorClient();
  vi.spyOn(client, "current").mockReturnValue(answer);
  return client;
}

describe("useConnectorReadOnly", () => {
  it("is true for a connector that reports itself read-only", async () => {
    const readOnly = useConnectorReadOnly(clientAnswering(Promise.resolve({ readOnly: true } as Connector)));

    await settled();

    expect(readOnly.value).toBe(true);
  });

  it("is false for a connector that accepts changes", async () => {
    const readOnly = useConnectorReadOnly(clientAnswering(Promise.resolve({ readOnly: false } as Connector)));

    await settled();

    expect(readOnly.value).toBe(false);
  });

  it("is false when no connector is configured", async () => {
    const readOnly = useConnectorReadOnly(clientAnswering(Promise.resolve(null)));

    await settled();

    expect(readOnly.value).toBe(false);
  });

  it("stays false when the connector cannot be read", async () => {
    const readOnly = useConnectorReadOnly(clientAnswering(Promise.reject(new Error("unavailable"))));

    await settled();

    expect(readOnly.value).toBe(false);
  });
});
