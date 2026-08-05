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

import type AdvisorProjectFolder from '@/services/api/model/AdvisorProjectFolder';

/**
 * The list logic behind the source-folder picker, kept out of the component so it can be read and
 * tested on its own.
 */

/**
 * Narrows the saved folders to those matching what was typed. Both the name and the path are
 * searched: the name is what the folder is known by, but a path fragment is the other thing anyone
 * would reach for when two checkouts share a name.
 */
export function filterFolders(
  folders: AdvisorProjectFolder[],
  query: string
): AdvisorProjectFolder[] {
  const term = query.trim().toLowerCase();
  if (!term) {
    return folders;
  }
  return folders.filter(
    folder => folder.name.toLowerCase().includes(term) || folder.path.toLowerCase().includes(term)
  );
}

/**
 * The saved folder a stored path belongs to, or null when the path was typed by hand. Trailing
 * separators are ignored so `/src/app/` and `/src/app` are not treated as different folders.
 */
export function findByPath(
  folders: AdvisorProjectFolder[],
  path: string | null | undefined
): AdvisorProjectFolder | null {
  const wanted = normalizePath(path);
  if (!wanted) {
    return null;
  }
  return folders.find(folder => normalizePath(folder.path) === wanted) ?? null;
}

/** The first folder that can actually be run against — a missing one is not an answer. */
export function firstSelectable(folders: AdvisorProjectFolder[]): AdvisorProjectFolder | null {
  return folders.find(folder => folder.present) ?? null;
}

function normalizePath(path: string | null | undefined): string {
  const trimmed = (path ?? '').trim();
  if (trimmed.length <= 1) {
    return trimmed;
  }
  return trimmed.replace(/\/+$/, '');
}
