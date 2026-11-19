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

export interface IoOverview {
  bytesRead: number;
  bytesWritten: number;
  opCount: number;
  slowestNanos: number;
  slowestTarget: string | null;
  hasEvents: boolean;
}

export interface IoOperation {
  kind: string;
  target: string;
  bytes: number;
  durationNanos: number;
  thread: string | null;
}

export interface IoEndpoint {
  target: string;
  opCount: number;
  bytes: number;
  totalNanos: number;
  maxNanos: number;
}

/**
 * One endpoint's totals paired with its per-second shape, so a gallery of peers can be drawn as
 * sparkline tiles from a single request. The serie holds bytes or operations depending on the
 * `IoMetric` the gallery was requested with, and carries its own name so the unit travels with it.
 */
export interface IoEndpointTimeline {
  endpoint: IoEndpoint;
  serie: {
    name: string;
    data: number[][];
  };
}

export interface FileForceOp {
  timeOffsetMillis: number;
  path: string | null;
  metaData: boolean;
  durationNanos: number;
  thread: string | null;
}

export interface FileForceStats {
  count: number;
  totalNanos: number;
  avgNanos: number;
  maxNanos: number;
  metadataCount: number;
  slowest: FileForceOp[];
}
