/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
