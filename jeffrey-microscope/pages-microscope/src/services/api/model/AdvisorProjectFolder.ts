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
 * A named working copy on this machine the Advisor may read. Nothing ties it to a workspace, project
 * or hub: the name is the user's own, and it is what identifies the folder when it is picked for a run.
 */
export default interface AdvisorProjectFolder {
  folderId: string;
  name: string;
  path: string;
  /** Whether the path resolves to a folder right now — a moved checkout stays listed, but missing. */
  present: boolean;
  createdAt: number;
}

/** The editable payload sent when creating or updating a folder (server assigns id/createdAt). */
export type AdvisorProjectFolderRequest = Pick<AdvisorProjectFolder, 'name' | 'path'>;
