export interface FillDistribution {
    empty: number;
    low: number;
    medium: number;
    high: number;
    full: number;
}

export interface CollectionStats {
    collectionType: string;
    totalCount: number;
    emptyCount: number;
    totalWastedBytes: number;
    avgFillRatio: number;
    fillDistribution: FillDistribution;
}

export default interface CollectionAnalysisReport {
    totalCollections: number;
    totalEmptyCount: number;
    totalWastedBytes: number;
    overallFillDistribution: FillDistribution;
    byType: CollectionStats[];
}
