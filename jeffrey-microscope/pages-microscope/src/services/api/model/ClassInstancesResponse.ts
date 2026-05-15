export interface ClassInstanceEntry {
  objectId: number;
  shallowSize: number;
  retainedSize: number | null;
  objectParams: Record<string, string>;
  contentPreview: string | null;
}

export default interface ClassInstancesResponse {
  className: string;
  totalInstances: number;
  instances: ClassInstanceEntry[];
  hasMore: boolean;
}
