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

/** Which storage the connector reads: an Azure file share, or a directory on Microscope's machine. */
export type ConnectorKind = 'AZURE_FILES' | 'LOCAL';

/**
 * The one connector this Microscope reaches, the recording volume on an Azure file share or in a
 * local directory, as configuration declares it.
 */
export default interface Connector {
  name: string;
  kind: ConnectorKind;
  /**
   * Where the volume lies, as a person reads it: the share's endpoint, name and directory, or the
   * local directory's absolute path.
   */
  location: string;
  /**
   * A connector declared read-only (`read-only=true` in its block): no workspace, project or session
   * is deleted through it.
   */
  readOnly: boolean;
}
