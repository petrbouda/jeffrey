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

import { PathStep } from '@/services/api/model/GCRootPath';

export interface DominatedClassEntry {
  className: string;
  instanceCount: number;
  retainedSize: number;
  percentOfCluster: number;
}

export interface ClassLoaderLeakSummary {
  classLoaderId: number;
  classLoaderClassName: string;
  totalRetainedSize: number;
  suspectCount: number;
}

export interface LeakSuspect {
  rank: number;
  className: string;
  objectId: number | null;
  retainedSize: number;
  heapPercentage: number;
  instanceCount: number;
  reason: string;
  accumulationPoint: string;
  pathSteps: PathStep[];
  accumulationPointId: number | null;
  accumulationPointClass: string | null;
  dominatedHistogram: DominatedClassEntry[];
  leakScore: number;
  classLoaderId: number;
  classLoaderClassName: string;
}

export default interface LeakSuspectsReport {
  totalHeapSize: number;
  analyzedBytes: number;
  suspects: LeakSuspect[];
  topLeakingClassLoaders: ClassLoaderLeakSummary[];
}
