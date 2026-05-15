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

/**
 * One row of the "Largest String Instances" table — a single String object
 * ranked by sharing-aware GC-retained size. content is always a truncated
 * preview, even when the backing string exceeded the indexer's cap (the
 * backend re-decodes from the heap dump in that case).
 */
export default interface StringInstanceEntry {
  content: string;
  instanceId: number;
  arrayShallowSize: number;
  arrayRefCount: number;
  retainedSize: number;
}
