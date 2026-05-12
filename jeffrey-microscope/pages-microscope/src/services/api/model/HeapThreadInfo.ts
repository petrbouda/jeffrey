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

export default interface HeapThreadInfo {
  objectId: number;
  name: string;
  daemon: boolean;
  priority: number;
  retainedSize?: number;
  /** Number of stack frames at dump time. Absent for threads with no STACK_TRACE record. */
  frameCount?: number;
  /** Number of locals referenced across all frames (ROOT_JAVA_FRAME entries). */
  localsCount?: number;
  /** Sum of shallow sizes across all frame-local references, in bytes. */
  localsBytes?: number;
  /** Heuristic Thread.State approximation derived from the top frame's class.method:
   *  PARKED · WAITING · SLEEPING · NATIVE · RUNNABLE. */
  state?: string;
}
