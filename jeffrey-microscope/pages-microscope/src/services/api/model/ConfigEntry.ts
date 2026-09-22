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


export type ConfigScope = 'GLOBAL' | 'WORKSPACE' | 'PROJECT';

/**
 * What a published value configures. Closed on purpose: a value becomes a JVM option inside an
 * application's own argfile, so the set of things that can be said is a security boundary rather
 * than a convenience. Mirrors ConfigType on the backend.
 */
export type ConfigType = 'ASPROF_SETTINGS';

/**
 * One stored configuration value, carrying the scope it belongs to.
 *
 * A scope holds at most one value per type, so the API answers with a flat list and the caller
 * groups by scope. `workspaceId` is null for the global scope and `projectId` for everything but
 * a project.
 */
export default interface ConfigEntry {
  scope: ConfigScope;
  workspaceId: string | null;
  projectId: string | null;
  type: ConfigType;
  value: string;
  updatedAt: number;
}

/** The value of one type within a scope, or null when that scope does not set it. */
export function entryOf(entries: ConfigEntry[], scope: ConfigScope, type: ConfigType): ConfigEntry | null {
  return entries.find((entry) => entry.scope === scope && entry.type === type) ?? null;
}
