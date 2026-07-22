export interface ConsumerEntry {
  packageName: string;
  classLoaderId: number;
  classLoaderClassName: string | null;
  retainedSize: number;
  shallowSize: number;
  classCount: number;
  instanceCount: number;
}

export interface ComponentEntry {
  packageName: string;
  retainedSize: number;
  shallowSize: number;
  classCount: number;
  instanceCount: number;
}

export default interface ConsumerReport {
  totalHeapSize: number;
  topConsumers: ConsumerEntry[];
  componentReport: ComponentEntry[];
}
