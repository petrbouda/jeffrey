import type { ClassLoaderUnloadability, LoaderType } from '@/services/api/model/ClassLoaderReport';

export interface ClassEntry {
  classId: number;
  name: string;
  instanceCount: number;
  totalInstanceSize: number;
}

export default interface ClassLoaderDetail {
  loaderId: number;
  displayName: string;
  parentLoaderId: number;
  parentDisplayName: string;
  type: LoaderType;
  unloadability: ClassLoaderUnloadability;
  retainedSize: number;
  classCount: number;
  instanceCount: number;
  classes: ClassEntry[];
}
