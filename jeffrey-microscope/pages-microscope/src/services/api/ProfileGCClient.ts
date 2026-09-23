/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import BaseProfileClient from '@/services/api/BaseProfileClient';
import GCOverviewData from '@/services/api/model/GCOverviewData';
import GCConfigurationData from '@/services/api/model/GCConfigurationData';
import GCTimeseriesType from '@/services/api/model/GCTimeseriesType';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import type GCPhaseParallelAggregate from '@/services/api/model/GCPhaseParallelAggregate';
import type G1PlabStatistics from '@/services/api/model/G1PlabStatistics';
import type { IhopData, TenuringData } from '@/services/api/model/GCTuningModels';
import type G1AnalysisData from '@/services/api/model/G1AnalysisData';
import type ZgcAnalysisData from '@/services/api/model/ZgcAnalysisData';
import type { StringSymbolTablesData, FinalizersData } from '@/services/api/model/GCTablesModels';
import type { ReferenceProcessingData } from '@/services/api/model/GCReferenceModels';

export default class ProfileGCClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'gc');
  }

  public getOverview(): Promise<GCOverviewData> {
    return this.get<GCOverviewData>('');
  }

  public getTimeseries(timeseriesType: GCTimeseriesType): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/timeseries', { timeseriesType });
  }

  public getConfiguration(): Promise<GCConfigurationData> {
    return this.get<GCConfigurationData>('/configuration');
  }

  public getTenuring(): Promise<TenuringData> {
    return this.get<TenuringData>('/tenuring');
  }

  public getIhop(): Promise<IhopData> {
    return this.get<IhopData>('/ihop');
  }

  public getG1Analysis(): Promise<G1AnalysisData> {
    return this.get<G1AnalysisData>('/g1');
  }

  public getZgcAnalysis(): Promise<ZgcAnalysisData> {
    return this.get<ZgcAnalysisData>('/zgc');
  }

  public getStringSymbolTables(): Promise<StringSymbolTablesData> {
    return this.get<StringSymbolTablesData>('/string-symbol-tables');
  }

  public getFinalizers(): Promise<FinalizersData> {
    return this.get<FinalizersData>('/finalizers');
  }

  public getReferenceProcessing(): Promise<ReferenceProcessingData> {
    return this.get<ReferenceProcessingData>('/reference-processing');
  }

  public getPhaseParallel(): Promise<GCPhaseParallelAggregate[]> {
    return this.get<GCPhaseParallelAggregate[]>('/phase-parallel');
  }

  public getPlabStatistics(): Promise<G1PlabStatistics[]> {
    return this.get<G1PlabStatistics[]>('/plab-statistics');
  }
}
