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

/** A Jeffrey Hub reached over gRPC, or the recording volume reached straight on an Azure file share. */
export type RemoteKind = 'GRPC' | 'AZURE_FILES';

/** The one remote this Microscope reaches, as configuration declares it. */
export default interface Remote {
  name: string;
  kind: RemoteKind;
  /** Where the remote lies, as a person reads it: `host:port`, or the share's endpoint and name. */
  location: string;
  /**
   * A remote declared read-only (`azure-files.read-only=true`): no workspace is created or
   * deleted, and no session deleted or pinned, through it.
   */
  readOnly: boolean;
}
