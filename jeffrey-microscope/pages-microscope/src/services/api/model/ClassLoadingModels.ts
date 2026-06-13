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

export interface ClassLoadingOverview {
  currentlyLoaded: number;
  totalLoaded: number;
  totalUnloaded: number;
  classLoaderCount: number;
  metaspaceUsedBytes: number;
  hiddenClassCount: number;
  hasClassLoadEvents: boolean;
  hasRedefinitionEvents: boolean;
}

export interface ClassLoaderStat {
  name: string;
  parentName: string | null;
  classCount: number;
  metaspaceBytes: number;
  blockBytes: number;
  hiddenClassCount: number;
  hiddenMetaspaceBytes: number;
}

export interface ClassLoadEntry {
  className: string | null;
  durationNanos: number;
  definingClassLoader: string | null;
}

export interface ClassLoadActivity {
  totalCount: number;
  slowest: ClassLoadEntry[];
}

export interface ClassRedefinitionStat {
  className: string | null;
  modificationCount: number;
  redefinitionId: number;
}

export interface RetransformBatch {
  redefinitionId: number;
  classCount: number;
  durationNanos: number;
}

export interface RedefinitionData {
  redefinitions: ClassRedefinitionStat[];
  retransforms: RetransformBatch[];
}
