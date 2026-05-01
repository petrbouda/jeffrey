/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
