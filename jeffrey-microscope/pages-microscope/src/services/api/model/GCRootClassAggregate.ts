export default interface GCRootClassAggregate {
  className: string;
  rootCount: number;
  rootKinds: string[];
  totalRetainedBytes: number;
}
