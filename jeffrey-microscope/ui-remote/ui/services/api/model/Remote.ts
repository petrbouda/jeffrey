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

/** The one remote this Microscope reaches, the recording volume on an Azure file share, as configuration declares it. */
export default interface Remote {
  name: string;
  /** Where the share lies, as a person reads it: its endpoint, name and directory. */
  location: string;
  /**
   * A remote declared read-only (`azure-files.read-only=true`): no workspace, project or session
   * is deleted through it.
   */
  readOnly: boolean;
}
