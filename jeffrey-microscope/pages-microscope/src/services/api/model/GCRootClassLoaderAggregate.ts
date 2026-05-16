export default interface GCRootClassLoaderAggregate {
  classloaderObjectId: number | null;
  classloaderClass: string;
  rootCount: number;
  totalRetainedBytes: number;
}
