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
 * One endpoint's totals paired with its bytes-per-second shape, so a gallery of peers can be drawn
 * as sparkline tiles from a single request.
 */
export interface IoEndpointTimeline {
  endpoint: IoEndpoint;
  throughput: {
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
