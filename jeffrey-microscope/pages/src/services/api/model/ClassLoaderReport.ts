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

import { GCRootPath } from '@/services/api/model/GCRootPath';

export interface ClassLoaderInfo {
  objectId: number;
  classLoaderClassName: string;
  classCount: number;
  totalClassSize: number;
  retainedSize: number;
}

export interface DuplicateClassInfo {
  className: string;
  loaderCount: number;
  classLoaderNames: string[];
}

export type HintKind =
  | 'THREAD_LOCAL'
  | 'JDBC_DRIVER'
  | 'JNI_GLOBAL'
  | 'SERVICE_LOADER'
  | 'LOGGER'
  | 'CONTEXT_CLASSLOADER';

export interface CauseHint {
  kind: HintKind;
  description: string;
  objectId: number;
}

export interface ClassLoaderLeakChain {
  classLoaderId: number;
  classLoaderClassName: string;
  classCount: number;
  totalClassSize: number;
  retainedSize: number;
  gcRootPath: GCRootPath | null;
  causeHints: CauseHint[];
  hasDuplicateClasses: boolean;
}

export interface ClassLoaderHierarchyEdge {
  childId: number;
  parentId: number;
}

export type UnloadabilityVerdict = 'UNLOADABLE' | 'PINNED_ROOTED' | 'PINNED_TRANSITIVE';

export type LoaderType = 'BOOTSTRAP' | 'PLATFORM' | 'SYSTEM' | 'WEB' | 'OSGI' | 'APP' | 'CUSTOM';

export interface BlockingClass {
  classId: number;
  name: string;
  instanceCount: number;
  totalInstanceSize: number;
}

export interface ClassLoaderUnloadability {
  verdict: UnloadabilityVerdict;
  liveInstanceCount: number;
  rooted: boolean;
  topBlockingClasses: BlockingClass[];
}

export default interface ClassLoaderReport {
  totalClassLoaders: number;
  totalClasses: number;
  duplicateClassCount: number;
  classLoaders: ClassLoaderInfo[];
  duplicateClasses: DuplicateClassInfo[];
  leakChains: ClassLoaderLeakChain[];
  hierarchyEdges: ClassLoaderHierarchyEdge[];
  unloadability: Record<string, ClassLoaderUnloadability>;
  loaderTypes: Record<string, LoaderType>;
}
