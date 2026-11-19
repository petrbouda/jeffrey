/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
import Serie from '@/services/timeseries/model/Serie';

export interface GrpcOverviewData {
  header: GrpcHeader;
  services: GrpcServiceInfo[];
  statusCodes: GrpcStatusStats[];
  slowCalls: GrpcSlowCall[];
  responseTimeSerie: Serie;
  callCountSerie: Serie;
}

export interface GrpcServiceDetailData {
  header: GrpcHeader;
  methods: GrpcMethodInfo[];
  statusCodes: GrpcStatusStats[];
  slowCalls: GrpcSlowCall[];
  responseTimeSerie: Serie;
  callCountSerie: Serie;
}

export interface GrpcTrafficData {
  header: GrpcHeader;
  requestSizeSerie: Serie;
  responseSizeSerie: Serie;
  sizeBuckets: GrpcSizeBucket[];
  largestCalls: GrpcLargestCall[];
}

export interface GrpcHeader {
  callCount: number;
  maxResponseTime: number;
  p99ResponseTime: number;
  p95ResponseTime: number;
  successRate: number;
  errorCount: number;
  totalBytesSent: number;
  totalBytesReceived: number;
  avgRequestSize: number;
  avgResponseSize: number;
  maxRequestSize: number;
  maxResponseSize: number;
}

export interface GrpcServiceInfo {
  service: string;
  callCount: number;
  maxResponseTime: number;
  p99ResponseTime: number;
  p95ResponseTime: number;
  successRate: number;
  avgRequestSize: number;
  avgResponseSize: number;
}

export interface GrpcMethodInfo {
  method: string;
  callCount: number;
  maxResponseTime: number;
  p99ResponseTime: number;
  p95ResponseTime: number;
  successRate: number;
  avgRequestSize: number;
  avgResponseSize: number;
}

export interface GrpcStatusStats {
  status: string;
  count: number;
}

export interface GrpcSlowCall {
  service: string;
  method: string;
  responseTime: number;
  status: string;
  requestSize: number;
  responseSize: number;
  host: string;
  port: number;
  timestamp: number;
}

export interface GrpcLargestCall {
  service: string;
  method: string;
  requestSize: number;
  responseSize: number;
  totalSize: number;
  responseTime: number;
  status: string;
  timestamp: number;
}

export interface GrpcSizeBucket {
  label: string;
  count: number;
}

export default class ProfileGrpcClient extends BaseProfileClient {
  private readonly mode: string;

  constructor(mode: 'client' | 'server', profileId: string) {
    super(profileId, 'grpc/overview');
    this.mode = mode;
  }

  public getOverview(): Promise<GrpcOverviewData> {
    return super.get<GrpcOverviewData>('', { mode: this.mode });
  }

  public getServiceDetail(service: string): Promise<GrpcServiceDetailData> {
    return super.get<GrpcServiceDetailData>('/service', { service, mode: this.mode });
  }

  public getTraffic(): Promise<GrpcTrafficData> {
    return super.get<GrpcTrafficData>('/traffic', { mode: this.mode });
  }

  public getTrafficByService(service: string): Promise<GrpcTrafficData> {
    return super.get<GrpcTrafficData>('/traffic/service', { service, mode: this.mode });
  }
}
