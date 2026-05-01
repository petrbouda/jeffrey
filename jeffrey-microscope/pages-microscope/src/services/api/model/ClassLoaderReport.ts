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

export default interface ClassLoaderReport {
  totalClassLoaders: number;
  totalClasses: number;
  duplicateClassCount: number;
  classLoaders: ClassLoaderInfo[];
  duplicateClasses: DuplicateClassInfo[];
}
