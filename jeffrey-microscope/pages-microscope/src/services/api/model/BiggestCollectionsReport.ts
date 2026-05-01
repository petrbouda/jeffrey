export interface BiggestCollectionEntry {
  objectId: number;
  className: string;
  elementCount: number;
  capacity: number;
  fillRatio: number;
  shallowSize: number;
  retainedSize: number;
  ownerClassName: string | null;
}

export default interface BiggestCollectionsReport {
  totalCollectionsAnalyzed: number;
  byElementCount: BiggestCollectionEntry[];
  byRetainedSize: BiggestCollectionEntry[];
}
