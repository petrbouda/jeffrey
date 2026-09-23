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
