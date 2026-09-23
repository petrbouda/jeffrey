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

import type TimeseriesData from '@/services/timeseries/model/TimeseriesData';

export interface SecurityHeader {
  tlsHandshakes: number;
  distinctPeers: number;
  certificates: number;
  flaggedCertificates: number;
  deserializationEvents: number;
  deserializationRejected: number;
}

export interface NamedCount {
  name: string;
  count: number;
}

export interface CertificateStat {
  subject: string;
  issuer: string;
  keyType: string;
  keyLength: number;
  signatureAlgorithm: string;
  validFrom: number;
  validUntil: number;
  validationCount: number;
  weakKey: boolean;
  weakSignature: boolean;
  expired: boolean;
  expiringSoon: boolean;
}

export interface DeserializationSummary {
  totalEvents: number;
  filterConfiguredEvents: number;
  rejectedEvents: number;
  exceptionEvents: number;
}

export interface DeserializationTypeStat {
  type: string;
  count: number;
  totalBytes: number;
  maxBytes: number;
  maxDepth: number;
}

export interface ProviderServiceStat {
  provider: string;
  type: string;
  algorithm: string;
  count: number;
}

export interface MisdeclarationStat {
  misdeclaredClass: string;
  message: string;
  count: number;
}

export default interface SecurityData {
  header: SecurityHeader;
  tlsTimeline: TimeseriesData;
  protocols: NamedCount[];
  ciphers: NamedCount[];
  peers: NamedCount[];
  certificates: CertificateStat[];
  deserialization: DeserializationSummary;
  deserializationTypes: DeserializationTypeStat[];
  serializationMisdeclarations: MisdeclarationStat[];
  cryptoProviders: ProviderServiceStat[];
}
