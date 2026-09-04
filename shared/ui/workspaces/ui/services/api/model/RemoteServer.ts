/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

export type HubSource = 'CONFIG' | 'USER';

export default interface RemoteServer {
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
