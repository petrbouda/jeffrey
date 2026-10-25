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

export type RemoteSource = 'CONFIG' | 'USER';

/** A Jeffrey Hub reached over gRPC, or the recording volume reached straight on an Azure file share. */
export type RemoteKind = 'GRPC' | 'AZURE_FILES';

/** The one remote this Microscope is connected to. */
export default interface Remote {
  name: string;
  /** The hub's host and port; null for an Azure file share. */
  hostname: string | null;
  port: number | null;
  plaintext: boolean;
  createdAt: number;
  /**
   * 'CONFIG' when the remote is declared under jeffrey.microscope.remote.* — the UI cannot
   * disconnect it, since the next startup would connect it again.
   */
  source: RemoteSource;
  kind: RemoteKind;
  /** Where the remote lies, as a person reads it: `host:port`, or the share's endpoint and name. */
  location: string;
  /**
   * A remote declared read-only (`azure-files.read-only=true`): no workspace is created or
   * deleted, and no session deleted or pinned, through it.
   */
  readOnly: boolean;
}

/** What GET /remote answers: the connected remote, or null when none is. */
export interface CurrentRemote {
  remote: Remote | null;
}
