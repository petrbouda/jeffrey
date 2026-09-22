/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

/** One hub-published configuration file a session merged, and which version of it. */
export interface AppliedConfigLayer {
  scope: 'GLOBAL' | 'WORKSPACE' | 'PROJECT';
  digest: string;
}

export default class ProjectInstanceSession {
  constructor(
    public id: string,
    public repositoryId: string,
    public createdAt: number,
    public duration: number,
    public finishedAt?: number,
    public isActive?: boolean,
    /** Finished without producing any data (zero bytes) — e.g. a crash-looped container. */
    public failed?: boolean,
    /**
     * Which configuration layer supplied the profiler command: a layer inside the container, one of
     * the Hub-published scopes, or the Provisioner's built-in default. Absent for a session
     * declared by a Provisioner too old to record it.
     */
    public profilerCommandSource?: string,
    /** The resolved profiler command this session was started with. */
    public profilerCommand?: string,
    /**
     * The Hub-published files this session merged, with the digest each had when it was read.
     * Comparing them with what the Hub holds now says whether this JVM is still current.
     */
    public configLayers?: AppliedConfigLayer[]
  ) {}
}
