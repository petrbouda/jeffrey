export default interface GCRootRetainer {
  objectId: number;
  className: string;
  rootKind: string;
  shallowSize: number;
  retainedSize: number;
}
