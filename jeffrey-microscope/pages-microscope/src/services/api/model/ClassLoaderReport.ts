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

export default interface ClassLoaderReport {
  totalClassLoaders: number;
  totalClasses: number;
  duplicateClassCount: number;
  classLoaders: ClassLoaderInfo[];
  duplicateClasses: DuplicateClassInfo[];
  leakChains: ClassLoaderLeakChain[];
}
