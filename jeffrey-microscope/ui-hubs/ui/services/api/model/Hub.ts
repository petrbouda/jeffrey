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

export type HubSource = 'CONFIG' | 'USER';

export default interface Hub {
  id: string;
  name: string;
  hostname: string;
  port: number;
  plaintext: boolean;
  createdAt: number;
  /**
   * 'CONFIG' when the hub is declared under jeffrey.microscope.hubs.* — the UI shows it as
   * read-only, since the next startup recreates anything deleted here. Optional so a backend
   * that does not send it degrades to the editable behaviour rather than breaking.
   */
  source?: HubSource;
}
